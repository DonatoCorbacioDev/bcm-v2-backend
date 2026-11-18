# Secure code review

How a change to BCM is reviewed for security (OWASP Secure Code Review Cheat
Sheet, reviewed 2026-11-18): where untrusted data enters and what stops it on
the way to where it is used, which tool checks what on every push, and the
checklist for a change. The threats and their residual risk are in
[SECURITY.md](./SECURITY.md); the endpoints and who may call them in
[API_INVENTORY.md](./API_INVENTORY.md).

## Data flow: from source to sink

Every row is one way untrusted data travels. A change that adds a source or a
sink adds a row here, and a test.

| Source (untrusted) | Control on the way | Sink | Proved by |
|---|---|---|---|
| Request body and query parameters | `@Valid` on every body, a length or range bound on every field (`InputLimits`); request DTOs carry only the fields a caller may set | Services, then the database through bound parameters only (Spring Data, one `JdbcTemplate` with `?`); `LIKE` patterns escaped (`LikePattern`) | `InputValidationGuardTest`, `InputValidationCheatSheetTest`, `SqlInjectionGuardTest`, `SqlInjectionMySQLIT` |
| An identifier in the path or body | The organization comes from the token, never the request (`TenantContext`, fails closed); records of a contract go through `ContractAccessGuard`; another organization's record answers 404 | Repositories, every query filtered by organization | `TenantScopeGuardTest`, `CrossTenantIsolationIT`, `BolaJwtMySQLIT`, `ContractAccessGuardTest`, the CI smoke test `bola_two_orgs.py` |
| The `Authorization` header | `JwtUtils`: one algorithm, the HMAC the key's length gives (HS384 in production), issuer, expiry, issue time, token type (a 2FA step token is not a session); session revocation | `SecurityContext`, then `@PreAuthorize` on every endpoint (deny by default) | `JwtUtilsTest`, `FunctionLevelAuthorizationTest`, `ApiInventoryTest` |
| An uploaded file (PDF, FatturaPA XML, XLSX) | `UploadChecks`: allowlisted type by content, size, ClamAV (fails closed), PDF active content, zip bomb limits; XML without DTDs (no XXE); stored under a generated name | Disk (`LocalStorageService`), parsers | `UploadGuardTest`, `UploadChecksTest`, `ClamAvScannerIT`, `FatturaPaXmlParserServiceTest` |
| Text stored by a user (names, contract numbers, notes) | Escaped for the place it lands | Email HTML (`HtmlUtils.htmlEscape`); Excel as text cells, never formulas; the frontend as React text (no `dangerouslySetInnerHTML`, no HTML from Markdown); logs through `oneLine()` and JSON in production | `EmailServiceTest`, `ExportServiceTest.formulaLikeTextStaysText`, `SecurityEventsTest`, FindSecBugs `CRLF_INJECTION_LOGS` |
| A question to the assistant, and Ollama's answer | Cost limit per user; the answer is shown as text; Ollama's errors stay in the log (`llm_errors.py`) | The frontend; the ML service's log | `CostlyOperationLimiterTest`, the ML service's tests |
| Calls the backend makes (ML service, Stripe, mail) | Fixed hosts from configuration, never from the request; no redirect followed; signed claims (`X-Internal-Claims`) bind organization and manager | The ML service, Stripe, SMTP | `MlProxyRedirectTest`, `InternalApiKeyGuardTest` |
| Stripe's webhook body | Signature verified with `STRIPE_WEBHOOK_SECRET` before anything is read | Subscription tier | `BillingServiceTest` |
| An exception | `GlobalExceptionHandler`: its text reaches the client only when this application wrote it; a 500 says nothing more | The HTTP response; the log | `GlobalExceptionHandlerTest`, `ErrorsOverHttpTest` |

## The layers and their guards

Each layer has a test that fails a change skipping its check, denied by
default: a new endpoint, query or upload must state what protects it.

| Layer | Guard |
|---|---|
| Controller | `FunctionLevelAuthorizationTest` (a role on every endpoint), `InputValidationGuardTest` (every body validated), `UploadGuardTest` (uploads only through `UploadChecks`), `LayeringGuardTest` (no controller reaches a repository: it goes through a service, where the checks are), `ApiInventoryTest` |
| Service | `ContractAccessGuard` and its test (record-level access), `PublicDemoGuardTest`, `InternalApiKeyGuardTest` |
| Repository | `TenantScopeGuardTest` (every query scoped to the organization), `SqlInjectionGuardTest` (no SQL built at runtime), `LeastPrivilegeMySQLIT` (the account the application runs with changes rows only) |

## Tools on every push

| Tool | Repository | What it checks | Fails the build on |
|---|---|---|---|
| CodeQL (`codeql.yml`, security-extended, also weekly) | backend, frontend, ml | Data flow: a value from a source reaching a sink with no check in between (injection, path traversal, XSS, SSRF, and more) | A finding of security severity 7.0 or more |
| SpotBugs + FindSecBugs (`mvn verify`) | backend | Bug and security patterns in Java (log injection, weak crypto, unsafe XML) | Any finding not excluded with its reason in `spotbugs-exclude.xml` |
| Bandit | ml | Security patterns in Python | Medium severity and up |
| ESLint | frontend | Code patterns (React, Next.js) | Errors |
| SonarCloud | backend, frontend, ml | Bugs, vulnerabilities, code smells, coverage | Its quality gate (reported on the commit) |
| Trivy on the SBOM | backend | Known vulnerabilities in every Maven dependency | HIGH or CRITICAL with a fix |
| `npm audit` | frontend | Known vulnerabilities in npm dependencies | High and up |
| pip-audit | ml | Known vulnerabilities in Python dependencies | Any |
| Trivy on the images (`image-scan.yml`, also weekly) | docker | Known vulnerabilities in all six images | HIGH or CRITICAL with a fix |
| gitleaks | all four | Secrets in the pushed commits | Any |
| Dependabot | all four | Newer versions of dependencies, base images and actions | (opens pull requests) |

## Checklist for a change

1. **New input?** Validated and bounded where it enters; a row in the data
   flow table above.
2. **New endpoint?** A `@PreAuthorize` with the narrowest role, a line in
   `API_INVENTORY.md`, and a test that the other roles get 403.
3. **New query?** Filtered by organization, values bound, never concatenated;
   the guards above pass without a new exception, or the exception says why.
4. **New sink** (a file, a page, an email, a log, an outbound call)? Escaped or
   encoded for that sink; nothing from the request decides where a call goes.
5. **Secrets?** None in code, logs or errors; a new one is a Docker secret.
6. **Errors?** The client gets a fixed message; the detail goes to the log.
7. **Security events?** A refusal or a change to credentials is recorded
   (`SecurityEvents`, `AuditLogService.saveAboutUser`).
8. **A security fix?** It ships with a test that fails without the fix: the
   vulnerability is reproduced first, then closed.
9. **Tools green:** CodeQL, SpotBugs, the dependency scans and the guards; a
   suppression only with its reason written next to it.
