# Security overview

This document summarizes the current threat model and the concrete steps required before
running BCM in production. It reflects the state of the codebase as audited on 2026-06-25,
not aspirational claims — update it whenever a control changes.

## Threat model (synthesis)

| Asset | Threat | Current mitigation | Residual risk |
|---|---|---|---|
| Tenant data (contracts, documents, invoices) | Cross-tenant data leak | `TenantContext` (populated from the JWT `orgId` claim by `JwtAuthenticationFilter`) scopes repository queries in services; covered by `CrossTenantAccessTest` | Scoping falls back to unscoped queries when `TenantContext` is `null` (e.g. scheduled jobs, or a JWT without an `orgId` claim). Acceptable for internal batch jobs that intentionally iterate all organizations; would be a bug if it ever happened on an authenticated HTTP request. |
| User credentials | Credential stuffing / brute force | BCrypt hashing, login rate limiting (`RateLimitingFilter`) | Rate limiting is in-memory and per-IP — see "Rate limiting" below |
| Access/refresh tokens | Token theft / replay | Short-lived access token, `HttpOnly`+`Secure`+`SameSite=Lax` refresh cookie scoped to `/auth`, refresh token rotation with reuse detection | None known |
| API surface | Information disclosure via API docs | Swagger UI / OpenAPI JSON disabled in the `prod` profile (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`) | None known |
| ML proxy (`MlProxyService` → FastAPI) | Unauthenticated access to the ML service | Shared `X-Internal-Api-Key` header, enforced by the backend on every proxied call | The ML service itself disables this check when its own `INTERNAL_API_KEY` is empty — must be set whenever the ML service is reachable outside the backend's trusted network (see bcm-v2-ml) |
| Uploaded documents | Malicious file upload / path traversal | 10MB max size, magic-byte PDF validation (not just `Content-Type`), storage path built from UUID + orgId/contractId (original filename never used in the path) | None known |
| Database | Default/weak credentials | Migrations do not embed real production secrets; `V4__create_admin_user.sql` seeds a default admin account, neutralized by `V14__neutralize_default_admin.sql` | The default admin's BCrypt hash is visible in migration history; rotate/disable it explicitly on every new deployment (see checklist) |

## Production security checklist

Before exposing this backend outside a trusted/internal network:

- [ ] Generate a fresh `JWT_SECRET` (≥256 bits, Base64) per environment — never reuse the dev/example value.
- [ ] Confirm `application-prod.properties` is active (`spring.profiles.active=prod`) so Swagger/API docs stay disabled.
- [ ] Set `ML_INTERNAL_API_KEY` (backend) / `INTERNAL_API_KEY` (bcm-v2-ml) to a strong random value — an empty value disables that check entirely.
- [ ] Rotate or disable the default admin account seeded by `V4__create_admin_user.sql` (already neutralized by `V14`, but confirm before going live with a fresh database).
- [ ] Confirm `FRONTEND_BASE_URL` is set to the real production origin — `CorsConfig` only restricts to it under the `prod` profile.
- [ ] Put a distributed rate limiter (Redis-backed, or an API gateway/WAF) in front of `/auth/**` — the built-in `RateLimitingFilter` is in-memory and per-IP, so it does not coordinate across multiple backend instances and resets on restart.
- [ ] Enable HTTPS only (terminate TLS in front of the app; cookies are marked `Secure`, so they will silently stop being sent over plain HTTP).
- [ ] Set up automated database backups and verify restore procedure.
- [ ] Configure log aggregation and alerting on `actuator/health` (and `metrics`/`info`, the only other exposed actuator endpoints).
- [ ] Add secret scanning to CI (gitleaks, already wired in `.github/workflows/ci.yml`) and document the rotation procedure if a secret is ever flagged: revoke immediately, issue a new one, redeploy, and confirm the old value no longer authenticates.
- [ ] Run `mvn spotbugs:check` and review the FindSecBugs report as part of the release process.

## Known limitations (won't fix without explicit need)

- **Rate limiting** is intentionally simple (in-memory, per-IP) for a single-instance dev/demo deployment. Scaling to multiple backend instances requires a shared store (Redis) or pushing the limiting to a gateway/WAF — tracked as a roadmap item, not built speculatively.
- **Tenant scoping fallback to unscoped queries when `TenantContext` is null** is by design for internal schedulers (`MonthlyReporter`, `RiskScoreRefresher`) that operate across all organizations. If this code path is ever reachable from an authenticated HTTP request, that is a bug — `CrossTenantAccessTest` exists to catch a regression in the common case (`GET /contracts`, `GET /contracts/{id}`).
