# BOLA authorization regression matrix

This matrix records the authorization evidence for API1:2023. Passing role tests
alone does not prove object authorization. The new matrices send actual signed
JWTs through the authentication filter, real domain services and repositories.
Fixtures contain two organizations and two managers in one organization.

## Expected behavior

| Scenario | Expected result |
| --- | --- |
| Authorized access to own resource | Successful response |
| Missing resource or resource in another organization | 404 |
| Plain manager accessing a colleague's contract | 403 |
| Child resource not belonging to the requested contract | 404 |
| Manager calling an admin-only operation | 403 |
| Missing JWT or signed JWT without an organization | 401 |
| Rejected write | Persisted business data unchanged; no storage or ML call |

Role restrictions take precedence when the caller cannot invoke an operation at
all. Lists and exports omit inaccessible objects rather than returning an error
for each excluded object. Workflow approvers may read a colleague's workflow in
the same organization; this does not allow access across organizations.

## Evidence by surface

| Surface | Coverage |
| --- | --- |
| Contracts, areas, managers, users, counterparties, financial types, budgets, templates, financial values | `BolaAuthorizationMatrixTest`: reads, updates and deletes with foreign and missing IDs, own reads and admin-only role checks |
| Contract manager assignment, collaborators and financial generation | JWT matrix for parent scope and roles; `ContractCollaboratorsAuthorizationIT` for committed writes and rollback |
| Contract history | JWT matrix for entry IDs and parent IDs, empty history, unassigned contracts and users without a manager profile |
| Document upload, versions, diff, download, terms, extraction and clause analysis | JWT matrix for parent scope, manager ownership and child/parent mismatch; domain services remain real |
| Invoice upload, list, metadata, download, deletion, payment details and matching | JWT matrix for parent scope, roles and child/parent mismatch; `InvoiceMatchAndNotificationAuthorizationTest` also exercises candidate scope and unchanged match state |
| SEPA creation, list and batch download | JWT matrix for parent scope, ownership and child/parent mismatch |
| Workflow submit, approve, reject and events | JWT matrix for parent scope and plain-manager denial; explicit same-tenant approver exception |
| Risk feedback | JWT matrix for parent scope and manager ownership |
| Financial-value relation IDs in request bodies | JWT matrix for foreign contract/type/area on creation and update; regression for moving an owned value to a colleague's contract |
| Other write references | Existing `CrossTenantAccessTest`, mapper tests and collaborator IT cover contract, area, manager, counterparty, template, budget, history and invite references |
| Notifications | JWT matrix for cross-user/tenant denial; existing real-service HTTP matrix covers own, foreign, missing and already-read notifications for both roles |
| Lists, search, filters and Excel/PDF exports | JWT matrix checks actual response contents and parses exported files; excludes foreign data and colleague contracts where required |
| Calendar feed | Explicit capability-token test without a JWT: only the token owner's contracts are present; invalid token is rejected |
| Global roles | Explicit test preserves globally shared role metadata |
| Internal organization operations | Ordinary tenant JWT cannot replace the separate internal API key |
| MySQL | `BolaJwtMySQLIT`: same JWT parent/ownership checks with committed fixtures on the Flyway schema; no surrounding test transaction |

The H2 matrix snapshots business tables before rejected operations and rereads
them after flushing and clearing the persistence context. Only external storage
and the ML proxy are mocked. MySQL fixtures are committed before HTTP requests
and cleaned by organization afterward. Existing native-SQL, import, foreign-key
and rollback ITs remain part of `mvn verify`.

## Findings and implemented fixes

- The project's custom `AccessDeniedException` does not extend Spring Security's
  exception and is not handled as HTTP 403. History and risk-feedback denial
  cases expose this discrepancy.
- `FinancialValueService.updateValue` checks the original contract but does not
  authorize the replacement contract. A manager can move an owned value to a
  colleague's contract in the same tenant. The regression expects 403 and an
  unchanged value.

Both fixes above were subsequently approved and implemented. The custom
exception now inherits from Spring Security's `AccessDeniedException`, and
financial-value updates authorize the replacement contract before mutation.
The 339-case JWT matrix passed after these changes.

The create-body ID tests reproduced another issue: POST endpoints for contracts,
financial values, contract history, business areas, managers, counterparties,
financial types and budgets preserved a caller-supplied ID in the mapped entity.
JPA `save` could therefore merge an existing foreign-tenant row instead of
inserting a new resource. All eight cases were also reproduced with committed
rows on real MySQL. Strengthening the user fixture to use an unassigned manager
revealed that user creation needed the same protection; an already-associated
manager had prematurely rejected the first test request. Template creation
already generated its own ID.

All nine creation paths now clear client IDs before saving. Users without a
manager are also explicitly assigned to the current tenant. The regressions
require either rejection without changes or a freshly generated ID and an
unchanged foreign resource; the implementation creates a new resource.
The extended matrices define 349 H2 cases and 69 MySQL cases, including creation
with a foreign ID and the separate workflow, calendar and internal-key rules.

This document does not certify a completed BOLA audit or the standalone ML
service. Production deployment is a separate authorization.

## Validation

Run the targeted matrices first, then `mvn verify` with Docker available, and
`mvn spotbugs:check`. Do not replace real MySQL integration tests with H2-only
results or disable failing authorization cases to obtain a green build.

The final full verification on 2026-11-09 passed 2,263 ordinary tests and 126
integration tests with no failures, errors or skipped tests, including all 349
H2 matrix cases and all 69 JWT/MySQL cases. All findings documented above have
implemented fixes. This is regression evidence for the covered surfaces, not a
claim that unrelated OWASP categories or the standalone ML service were audited.
