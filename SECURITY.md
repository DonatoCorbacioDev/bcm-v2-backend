# Security Policy

## 🔒 Supported Versions

The following versions of BCM Backend are currently supported with security updates:

| Version | Supported          | Status |
| ------- | ------------------ | ------ |
| 2.0.x   | ✅ Yes             | Active Development |
| 1.0.x   | ❌ No              | Legacy (Thesis Project) |

---

## 🐛 Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue in this project, **please report it responsibly**.

### How to Report

**📧 Email:** donatocorbacio92@gmail.com

**Subject:** `[SECURITY] BCM Backend - [Brief Description]`

**⚠️ DO NOT open a public GitHub issue for security vulnerabilities.**

### What to Include

Please provide as much information as possible:

1. **Description:** Clear explanation of the vulnerability
2. **Impact:** Potential consequences and severity assessment
3. **Steps to Reproduce:** Detailed reproduction steps
4. **Affected Components:** Files, endpoints, or features affected
5. **Suggested Fix:** If you have a solution (optional but appreciated)
6. **Your Contact Info:** For follow-up questions

### Example Report

```
Subject: [SECURITY] BCM Backend - SQL Injection in Contract Search

Description:
The /api/v1/contracts/search endpoint is vulnerable to SQL injection
via the 'customerName' query parameter.

Impact:
- Unauthorized data access
- Potential database manipulation
- Information disclosure

Steps to Reproduce:
1. Send GET request to /api/v1/contracts/search?customerName=' OR '1'='1
2. Observe all contracts returned regardless of permissions

Affected Components:
- ContractService.searchContracts() method
- ContractRepository custom query

Suggested Fix:
Use parameterized queries or Spring Data JPA specifications
```

---

## 🕐 Response Timeline

| Stage | Timeline | Description |
|-------|----------|-------------|
| **Acknowledgment** | 48 hours | Confirmation that we received your report |
| **Initial Assessment** | 5 days | Severity evaluation and validation |
| **Fix Development** | 7-30 days | Depending on complexity and severity |
| **Public Disclosure** | After fix | Coordinated disclosure after patch release |

### Severity Levels

- **Critical:** Immediate attention (24-48 hours)
- **High:** Within 7 days
- **Medium:** Within 14 days
- **Low:** Within 30 days

---

## 🛡️ Security Measures

### Current Implementation — verified against the actual code, not a point-in-time snapshot

#### Authentication & Authorization
- ✅ **JWT-based authentication** - Stateless, short-lived access token
- ✅ **Refresh token rotation with reuse detection** - hashed, single-use, rotated on every refresh (`RefreshTokenService`, see `docs/adr/0003-jwt-refresh-token-rotation.md`)
- ✅ **BCrypt password hashing**
- ✅ **Role-Based Access Control (RBAC)** - ADMIN and MANAGER roles, enforced via `@PreAuthorize` at the method level
- ✅ **Multi-Factor Authentication (TOTP)** - `TwoFactorAuthService`, opt-in from the profile page, recovery codes supported
- ✅ **Distributed login rate limiting** - `RateLimitingFilter`, Redis-backed (shared across instances), throttles `/auth/login` by client IP
- ✅ **Per-account lockout** - a second, independent control from the IP-based limiter above: `AuthService` locks an account after repeated failed attempts regardless of which IP they come from (`failed_login_attempts`/`locked_until` on `Users`, migration `V32`)
- ✅ **Email verification** - Required before account activation
- ✅ **Password reset tokens** - Time-limited, single-use tokens

#### Data Protection
- ✅ **SQL Injection Prevention** - JPA parameterized queries
- ✅ **XSS Protection** - Content Security Policy headers (frontend)
- ✅ **CSRF Protection** - Disabled at the framework level, but not exploitable: every authenticated request relies on the `Authorization` header (never auto-sent by browsers); the only cookie, the HttpOnly `refresh_token`, is scoped to `/auth` with `SameSite=Lax` (see `SecurityConfig`/`RefreshCookieFactory`)
- ✅ **Input Validation** - Hibernate Validator on all DTOs
- ✅ **IBAN/BIC masking** - masked in every API response (`IbanValidator`), full value never returned after creation
- ✅ **Audit logging with retention** - `AuditLogService`/`AuditAspect` on every create/update/delete, `AuditLogRetentionService` purges after a configurable window (180 days default)
- ✅ **Secure password storage** - Never logged or returned in API

#### Infrastructure
- ✅ **Environment variables** - Secrets in `.env` (git-ignored)
- ✅ **CORS configuration** - Restricted origins under the `prod` profile
- ✅ **`/actuator/prometheus` restricted** - to loopback/private IPs at the reverse-proxy layer (no scraper exposed publicly)
- ✅ **Database migrations** - Flyway, forward-only, version-controlled
- ✅ **Connection pooling** - HikariCP with limits

#### Code Quality
- ✅ **99% instruction / 96% branch / 99% line coverage** - see the badges at the top of `README.md`, backed by a two-tier test strategy (`docs/adr/0007-two-tier-test-strategy.md`): fast H2 unit suite + real-MySQL/Redis Testcontainers integration lane
- ✅ **Static analysis** - SpotBugs with FindSecBugs security rules, zero findings
- ✅ **Automated dependency scanning** - Dependabot (weekly + immediate security PRs), CycloneDX SBOM generated on every build, Gitleaks secret scanning in CI
- ✅ **No hardcoded secrets** - All configs externalized

---

## ⚠️ Known Limitations

This is a **portfolio/demonstration project moving toward production readiness** — see `docs/SECURITY.md` for the full threat model and production checklist. Real, current gaps:

### Not Yet Implemented

- ❌ **IP Whitelisting** - No network-level restrictions beyond the Prometheus endpoint above
- ❌ **Secrets Management** - Uses `.env` (would move to Vault/AWS Secrets Manager for a multi-environment/team setup)
- ❌ **DDoS Protection** - No WAF/CDN in front of the single VM (acceptable at current traffic, revisit if that changes)
- ❌ **Centralized metrics dashboard** - Prometheus metrics exist and are now access-restricted, but nothing scrapes/visualizes them yet
- ❌ **External penetration test** - Never done; self-assessed only

### Environment-Specific

#### Development
- ⚠️ Debug logging enabled
- ⚠️ H2 console accessible (test profile)
- ⚠️ Actuator endpoints exposed
- ⚠️ Detailed error messages

#### Production Recommendations
See "Production Security Checklist" below

---

## 🔐 Security Best Practices

### For Developers

**When Contributing:**

1. **Never commit secrets**
   - Check `.gitignore` includes `.env`, `application-*.properties`
   - Use environment variables for all credentials
   - Rotate any accidentally committed secrets immediately

2. **Input validation**
   - Validate all user inputs server-side
   - Use Hibernate Validator annotations
   - Sanitize data before database storage

3. **Authentication/Authorization**
   - Always use `@PreAuthorize` for sensitive endpoints
   - Test permission boundaries in unit tests
   - Follow principle of least privilege

4. **Dependencies**
   - Keep dependencies up-to-date
   - Review security advisories: `mvn dependency-check:check`
   - Avoid dependencies with known vulnerabilities

5. **Error handling**
   - Don't expose stack traces to clients
   - Log security events (failed auth, permission denied)
   - Use generic error messages externally

**Code Review Checklist:**

- [ ] No hardcoded credentials or secrets
- [ ] All endpoints have proper authorization
- [ ] Input validation on all user-provided data
- [ ] SQL queries use parameterized statements
- [ ] Error responses don't leak sensitive info
- [ ] New tests cover security boundaries
- [ ] Dependencies are up-to-date

---

## 🚀 Production Security Checklist

Before deploying to production:

### Infrastructure

- [x] **HTTPS Only** - Caddy + Let's Encrypt, HSTS (`max-age=63072000; includeSubDomains; preload`)
- [x] **Firewall Rules / Private Network** - MySQL bound to `127.0.0.1` only; from the internet only 22 and 443 are open
- [ ] **Load Balancer** - Single VM, no redundancy (accepted/documented risk at current scale)
- [ ] **CDN/WAF** - Not in front of the VM today
- [ ] **DDoS Protection** - No infrastructure-level rate limiting beyond the app-level limiter
- [x] **Database Backups** - Nightly `mysqldump`+uploads, age-encrypted, offsite copy (OCI Object Storage), restore verified with real production data, failure alerting via healthchecks.io
- [x] **Uptime Monitoring** - UptimeRobot on backend health + frontend, verified live with a real tested incident

### Application

- [ ] **Secrets Manager** - Still `.env`-based (Vault/AWS Secrets would matter once there's a team/multiple environments)
- [ ] **Database Credentials** - Rotation not on a schedule
- [x] **CORS Origins** - Restricted to `FRONTEND_BASE_URL` under the `prod` profile
- [x] **Actuator Endpoints** - `/actuator/prometheus` restricted to private IPs at the Caddy layer; `/health` stays public by design (polled by UptimeRobot)
- [x] **Error Messages** - Generic client-facing messages via `GlobalExceptionHandler`, details only in server-side logs
- [x] **Session Timeout** - `JWT_EXPIRATION_MS` configured, refresh token rotation on top
- [x] **Input Limits** - 10MB max upload, magic-byte validation on uploaded files

### Testing & Compliance

- [ ] **Penetration Testing** - Never done, self-assessed only
- [ ] **Formal OWASP Top 10 review** - Not documented as a standalone review (mitigations exist piecemeal, not checked off against the list)
- [x] **Dependency Scan** - Dependabot (weekly + security PRs) across all 4 repos, CycloneDX SBOM per build
- [ ] **GDPR Compliance** - Technical controls documented in `docs/GDPR.md`, explicitly **not** a substitute for legal review
- [ ] **Data Encryption at rest** - TLS in transit; no database-level encryption at rest
- [ ] **Incident Response Plan** - Backup/restore runbook exists; no broader written IR plan
- [ ] **Security Training** - N/A, single developer

### Monitoring

- [ ] **Failed Login Attempts** - Lockout happens (`AuthService`); no alert fires when it does
- [ ] **Unauthorized Access** - No alerting on 401/403 spikes
- [ ] **Unusual Activity** - ML anomaly detection exists for financial values, not security events
- [x] **Dependency Alerts** - GitHub Dependabot, all 4 repos

---

## 📚 Security Resources

### Standards & Guidelines

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [OWASP API Security Top 10](https://owasp.org/www-project-api-security/)
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)

### Tools

- **Static Analysis:** SpotBugs, FindSecBugs, SonarQube
- **Dependency Check:** OWASP Dependency-Check, Snyk
- **Penetration Testing:** OWASP ZAP, Burp Suite
- **Secrets Scanning:** TruffleHog, GitGuardian

### Training

- [OWASP Secure Coding Practices](https://owasp.org/www-project-secure-coding-practices-quick-reference-guide/)
- [Spring Security Training](https://spring.io/guides/topicals/spring-security-architecture/)

---

## 📞 Contact

**Security Contact:** donatocorbacio92@gmail.com  
**Project Maintainer:** Donato Corbacio  
**GitHub:** [@DonatoCorbacioDev](https://github.com/DonatoCorbacioDev)

**For non-security issues:** Please open a GitHub issue

---

## 📄 Disclosure Policy

We follow **coordinated disclosure**:

1. Reporter notifies us privately
2. We acknowledge and investigate
3. We develop and test a fix
4. We release a security patch
5. Public disclosure after users have time to update (typically 7-14 days)

**Hall of Fame:** Security researchers who responsibly disclose vulnerabilities will be acknowledged (with permission) in release notes and this document.

---

## 🆕 Security Updates

### Version 2.0.x (Current)

- **2026-10-16:** Full audit against the actual codebase — this document had drifted since its first draft and was still listing rate limiting, account lockout, MFA, refresh token rotation, and audit logging as "not yet implemented" long after all five shipped. Rewritten to match reality instead of a stale snapshot (same treatment `bcm-v2-frontend/SECURITY.md` got on 2026-09-06).
- **2025-02-05:** Initial security policy published
- **2025-01-15:** FindSecBugs integration added
- **2025-01-10:** JWT authentication implemented with BCrypt

### Planned Enhancements

- [ ] Centralized metrics dashboard (Grafana or similar) for the now-restricted Prometheus endpoint
- [ ] Documented secret-rotation runbook
- [ ] External penetration test / third-party security review

---

**Last Updated:** October 16, 2026
**Policy Version:** 2.0
