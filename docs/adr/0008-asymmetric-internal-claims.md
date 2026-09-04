# ADR-0008: Asymmetric signing (RS256) for the ML proxy's org_id/manager_id claim

**Status:** Accepted
**Date:** 2026-09-04

## Context

[ADR-0004](0004-separate-ml-service.md) authenticates backend→FastAPI calls with a shared
`X-Internal-Api-Key` header. That answers "is this caller allowed to reach the ML service at
all", but not "which org/manager is this specific request for" — `org_id`/`manager_id` are
plain query parameters FastAPI trusts at face value. Anyone in possession of the shared key
could request another organization's data by changing those parameters; the only thing
stopping this today is that the ML service is bound to `127.0.0.1` and never reachable from
outside the deployment (see `docs/deploy_status.md`), so the realistic blast radius is low —
but it's a real gap if that network assumption ever changes, or the key leaks by some channel
other than direct host compromise (accidental logging, a debug endpoint, etc.).

## Decision

The backend signs a short-lived (30s) JWT asserting `orgId`/`managerId`
(`MlClaimsSigner`), sent as `X-Internal-Claims` on every call. FastAPI verifies it
(`verify_internal_claims`) and, once a public key is configured, **ignores the raw query
params entirely** — they only matter as a fallback in local dev when no signing key is set,
matching the empty-`INTERNAL_API_KEY`-disables-the-check posture already established.

Signing is **asymmetric** (RS256), not a second shared secret (HMAC/symmetric JWT):
the backend holds the private key and signs; the ML service holds only the public key and
verifies. Reusing the same secret for both the API-key gate and the scope claim would add no
real protection — anyone who obtains that secret could forge both. Asymmetric signing means
compromising the ML container's environment (where the public key lives) does not grant the
ability to forge a different `org_id`/`manager_id`; only compromising the backend itself does,
which is a different, already-more-serious level of compromise this ADR isn't trying to cover.

## Alternatives considered

- **HMAC with a shared secret.** Rejected for the reason above: the verifying side would need
  the same secret used to sign, so leaking the ML service's environment leaks the ability to
  forge any scope — no better than today's plain query param, just with extra steps.
- **mTLS between backend and ML service.** Rejected as disproportionate: certificate issuance
  and rotation for a two-service, single-VM deployment is real operational overhead for a
  threat (network-level interception on a private Docker bridge, on a host an attacker would
  already need to control) that asymmetric claims already address more directly.
- **Doing nothing**, on the grounds that network isolation already contains the practical risk.
  Considered seriously — the security uplift here is genuinely marginal given that isolation —
  but the fix is cheap once designed (no new runtime dependency on the Java side, `PyJWT` is a
  small addition on the Python side) and closes the gap properly rather than relying solely on
  network topology remaining exactly as it is.

## Consequences

- **Positive:** `org_id`/`manager_id` are now a verified claim, not a trusted client input, for
  every ML call — forecast, risk scores, anomalies, agent insights/ask, and the nightly
  refreshers (`RiskScoreRefresher`/`MlCacheRefresher`, which assert an org-only claim with no
  manager scope, matching their batch nature).
- **Negative:** a new secret to manage (the private key, backend-only) and a new runtime
  dependency (`PyJWT[crypto]` — `cryptography` was already a dependency). Key generation is a
  manual one-time step (`util.InternalClaimsKeyGenerator`), not automated, since it only needs
  to happen once per environment and automating it would mean deciding where to persist a
  freshly generated private key on every startup — not worth it for a value that changes rarely.
- **Neutral:** local dev is unaffected — with no signing key configured, both sides fall back
  to today's query-param behavior, so `curl http://localhost:8000/forecast?org_id=1` keeps
  working without minting a token.
