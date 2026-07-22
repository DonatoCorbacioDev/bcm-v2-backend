# ADR-0003: Short-lived JWT + hashed, rotating refresh token in an `HttpOnly` cookie

**Status:** Accepted
**Date:** JWT introduced 2025, refresh token rotation + hashing added 2026-06-23, documented 2026-07-22

## Context

The API is stateless (`SessionCreationPolicy.STATELESS`), which rules out server-side sessions
for the primary access mechanism. But pure stateless JWT has no revocation story: a stolen
token stays valid until it naturally expires, and there's no way to force-logout a session.

## Decision

Two tokens, two different trust models:

- **Access token:** a short-lived, stateless JWT, sent as a normal `Authorization: Bearer`
  header, carrying the user's `orgId`/role claims. Never persisted server-side.
- **Refresh token:** a longer-lived, opaque token, persisted server-side **hashed with
  SHA-256** (never in plaintext — see V17, which migrated existing plaintext storage to
  hashed), delivered to the client only as an `HttpOnly`, `Secure`, `SameSite=Lax` cookie
  scoped to the `/auth` path (`RefreshCookieFactory`), so it's inaccessible to JavaScript and
  never sent on non-auth requests.
- **Rotation on every use:** each time a refresh token is exchanged for a new access token,
  the presented refresh token is revoked and a new one issued. If an already-revoked token is
  ever presented again — meaning it was stolen and used after the legitimate client already
  rotated past it — **every refresh token belonging to that user is revoked**, forcing
  re-authentication on all sessions (`RefreshTokenService`, see `"Refresh token reuse
  detected; all sessions revoked"`).

## Alternatives considered

- **Pure stateless JWT with a long expiry, no refresh token.** Rejected: no revocation
  mechanism at all. A stolen token (XSS, log leak, device theft) stays valid until it expires
  naturally, however long that is.
- **Server-side sessions** (e.g. Spring Session backed by Redis). Rejected: reintroduces the
  server-side state the stateless design exists to avoid, and adds Redis as an infrastructure
  dependency for a problem the JWT + refresh-token split already solves without one.
- **Refresh token in `localStorage`/response body** instead of an `HttpOnly` cookie. Rejected:
  directly readable by any JavaScript running on the page, including injected via XSS — the
  whole point of `HttpOnly` is that it isn't.
- **Refresh token stored in plaintext.** This was the original implementation; changed in V17
  once it became clear a database compromise (backup leak, SQL injection, insider access)
  would hand over directly usable long-lived tokens. Hashing means a DB leak alone isn't enough
  to impersonate a user — the attacker would still need a valid access token to have gotten
  that far, at which point the reuse-detection kicks in on the next legitimate refresh.

## Consequences

- **Positive:** revocable sessions, small JWT exposure window, and active detection (not just
  prevention) of token theft via the reuse check.
- **Positive:** cookie is unreachable from JavaScript (`HttpOnly`) and scoped to `/auth`
  (`path`), so it isn't sent on ordinary API calls and isn't a target for XSS exfiltration.
- **Negative:** more moving parts than a single JWT — rotation logic, a `refresh_tokens` table,
  and a `/auth/refresh` endpoint that is itself a meaningful piece of attack surface needing
  its own hardening (see `docs/SECURITY.md`).
- **Negative:** `SameSite=Lax` (not `Strict`) was a deliberate trade-off to let the refresh
  cookie survive top-level navigation from an external link into the app; this is standard
  practice but worth being able to explain, not just having chosen by default.
