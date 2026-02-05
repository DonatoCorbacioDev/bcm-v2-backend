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

### Current Implementation

#### Authentication & Authorization
- ✅ **JWT-based authentication** - Stateless token-based security
- ✅ **BCrypt password hashing** - Strength: 12 rounds (industry standard)
- ✅ **Role-Based Access Control (RBAC)** - ADMIN and MANAGER roles
- ✅ **Method-level security** - `@PreAuthorize` annotations
- ✅ **Email verification** - Required before account activation
- ✅ **Password reset tokens** - Time-limited, single-use tokens

#### Data Protection
- ✅ **SQL Injection Prevention** - JPA parameterized queries
- ✅ **XSS Protection** - Content Security Policy headers
- ✅ **CSRF Protection** - Disabled for stateless API (JWT-only)
- ✅ **Input Validation** - Hibernate Validator on all DTOs
- ✅ **Output Sanitization** - No sensitive data in responses
- ✅ **Secure password storage** - Never logged or returned in API

#### Infrastructure
- ✅ **Environment variables** - Secrets in `.env` (git-ignored)
- ✅ **CORS configuration** - Restricted origins (configurable)
- ✅ **Health checks** - Spring Actuator endpoints
- ✅ **Database migrations** - Flyway version control
- ✅ **Connection pooling** - HikariCP with limits

#### Code Quality
- ✅ **100% test coverage** - Comprehensive unit and integration tests
- ✅ **Static analysis** - SpotBugs with FindSecBugs security rules
- ✅ **Dependency scanning** - Maven dependency check
- ✅ **No hardcoded secrets** - All configs externalized

---

## ⚠️ Known Limitations

This is a **portfolio/demonstration project**. The following security considerations should be addressed before production use:

### Not Yet Implemented

- ❌ **Rate Limiting** - No request throttling (use API Gateway in prod)
- ❌ **Account Lockout** - No failed login attempt limits
- ❌ **Multi-Factor Authentication (MFA)** - Not implemented
- ❌ **Refresh Token Rotation** - JWT expires, no refresh mechanism
- ❌ **IP Whitelisting** - No network-level restrictions
- ❌ **Audit Logging** - Limited security event logging
- ❌ **Secrets Management** - Uses `.env` (use Vault/AWS Secrets in prod)
- ❌ **DDoS Protection** - No built-in protection (use Cloudflare/AWS Shield)
- ❌ **Web Application Firewall (WAF)** - Not configured

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

- [ ] **HTTPS Only** - Enforce TLS 1.2+ with valid certificates
- [ ] **Firewall Rules** - Restrict database access to backend only
- [ ] **Private Network** - Backend and DB on internal network
- [ ] **Load Balancer** - With SSL termination and health checks
- [ ] **CDN/WAF** - Cloudflare, AWS WAF, or similar
- [ ] **DDoS Protection** - Rate limiting at infrastructure level
- [ ] **Database Backups** - Automated, encrypted, tested restores
- [ ] **Monitoring/Alerting** - Security event monitoring (Datadog, New Relic)

### Application

- [ ] **Environment Variables** - Use secrets manager (AWS Secrets, HashiCorp Vault)
- [ ] **JWT Secret** - Rotate regularly, minimum 256 bits
- [ ] **Database Credentials** - Strong passwords (20+ chars), rotate quarterly
- [ ] **CORS Origins** - Whitelist specific domains only
- [ ] **Actuator Endpoints** - Secure or disable in production
- [ ] **Logging** - Enable audit logs, ship to SIEM
- [ ] **Error Messages** - Generic messages, detailed logs server-side
- [ ] **Session Timeout** - Configure appropriate JWT expiration
- [ ] **Input Limits** - Max request size, file upload limits

### Testing & Compliance

- [ ] **Penetration Testing** - Third-party security audit
- [ ] **OWASP Top 10** - Review and mitigate all risks
- [ ] **Dependency Scan** - No critical/high vulnerabilities
- [ ] **GDPR Compliance** - If handling EU user data
- [ ] **Data Encryption** - At rest and in transit
- [ ] **Incident Response Plan** - Documented procedures
- [ ] **Security Training** - Team aware of secure coding practices

### Monitoring

- [ ] **Failed Login Attempts** - Alert on brute force patterns
- [ ] **Unauthorized Access** - Alert on 401/403 spikes
- [ ] **Unusual Activity** - Detect anomalies (time, location, volume)
- [ ] **Dependency Alerts** - GitHub Dependabot or Snyk
- [ ] **Uptime Monitoring** - Health check endpoints

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

- **2025-02-05:** Initial security policy published
- **2025-02-05:** 100% test coverage achieved (all security boundaries tested)
- **2025-01-15:** FindSecBugs integration added
- **2025-01-10:** JWT authentication implemented with BCrypt

### Planned Enhancements

- [ ] Rate limiting implementation (Q2 2025)
- [ ] Refresh token rotation (Q2 2025)
- [ ] MFA support (Q3 2025)
- [ ] Enhanced audit logging (Q2 2025)

---

**Last Updated:** February 5, 2026  
**Policy Version:** 1.0
