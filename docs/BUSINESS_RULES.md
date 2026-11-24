# Business rules

The limits, flows and cross-field rules the backend enforces, in one place
(OWASP ASVS 5.0, V2.1: business logic documented). Every rule is enforced in
the service layer, so the form, a template, the CSV import and a direct API
call are treated alike; the frontend only mirrors some of them, to offer the
right choices. Each rule names the code that holds it and the test that
proves it. Keep this file in the same commit as a change to any of them.

## Contract status

Editing a contract (`PUT /contracts/{id}`) may move its status only as below
(`ContractStatusTransitions`, `ContractStatusTransitionsTest`; the edit form
offers the same choices, `contractStatusOptions` in the frontend). A refused
move answers 400 with the reason.

| From \ To | DRAFT | ACTIVE | EXPIRED | CANCELLED |
|---|---|---|---|---|
| **DRAFT** | yes | no: only approval activates a draft | no | yes |
| **ACTIVE** | no | yes | yes | yes |
| **EXPIRED** | no | yes, as a renewal: the new end date not already past, or none | yes | yes |
| **CANCELLED** | no | no | no | yes: final |

- Creating a contract is not a move: any status, so contracts already expired
  or cancelled can be imported.
- While a contract is in review its status cannot change at all.
- The system's own moves: the nightly job expires an active contract past its
  end date, or renews it tacitly when its terms say so (`ContractSchedulerService`).

## Approval workflow

DRAFT → IN_REVIEW → APPROVED (`ContractWorkflowService`). Approval also makes
the contract ACTIVE; rejection, with a required comment, sends it back to DRAFT.

- Submitted by the contract's manager or an administrator.
- Approved or rejected by an administrator, or a manager allowed to approve
  (`canApproveContracts`), who must not be the one who submitted it (four
  eyes; an administrator may decide on their own).
- Simultaneous decisions on the same contract: one wins, the others get 409
  or 400 (`TransactionIntegrityIT`).

## Cross-field rules

| Rule | Where |
|---|---|
| A contract's end date is not before its start date | `ContractService`, CHECK in MySQL (V62) |
| Amounts, values and file sizes are not negative | Bean Validation, CHECK in MySQL (V62) |
| A SEPA payment goes only to the supplier's verified IBAN | `SepaPaymentService` (`SEPA_IBAN_MISMATCH_BLOCKED`) |
| The first verified IBAN of a supplier is set by an administrator: a manager's first payment to it is refused until an administrator confirms the IBAN or generates that payment | `SepaPaymentService` (`SEPA_UNVERIFIED_IBAN_REFUSED`) |
| The same invoices are never in two SEPA batches | `SepaPaymentService` (`DUPLICATE_PAYMENT_REFUSED`); an invoice in a batch cannot be edited or deleted (`ElectronicInvoiceService`), nor written from an out-of-date copy (V64) |
| An invoice already loaded (supplier, number, document type) is refused | `ElectronicInvoiceService` |
| An edit made from an out-of-date copy is refused (409) | `@Version` on contracts, counterparties, organizations (V61), electronic invoices (V64) |

## Quotas per plan

| | FREE | PRO | ENTERPRISE | Code | Answer |
|---|---|---|---|---|---|
| Contracts | 15 | 200 | no limit | `TierLimitService` | 409 |
| Stored files (every document version and invoice) | 250 MB | 5 GB | no limit | `StorageQuotaService` | 409 |
| Forecasts, risk scores, assistant | no | yes | yes | `TierLimitService.requireMlAccess` | 403 |

Both quotas are counted with the organization's row locked, so simultaneous
requests cannot each take the last free place (`TransactionIntegrityIT`,
`StorageQuotaServiceTest`).

## Passwords

Wherever a password is set (registration, invitation, change, reset, an
administrator setting it): at least 8 characters, at most 72 bytes, no rule on
which characters (OWASP ASVS 6.2: composition rules make passwords weaker),
and then refused if it is

- one of the 46,000 most used of 8 characters or more (the NCSC's list,
  case-insensitive);
- built on the account's own words: its username, its organization's name, its
  person's names or email, or "bcm" (words of 4 letters or more);
- already leaked, according to Have I Been Pwned: only 5 hex characters of the
  password's SHA-1 leave the backend (k-anonymity); when the service does not
  answer within 2 seconds, the rules above decide alone.

`PasswordStrengthService`, `PasswordStrengthServiceTest`. The public demo
account is left out: its password is on the login page on purpose.

## Sessions

| What | Rule |
|---|---|
| Sessions per account | One: a new sign-in ends the previous one; signing out ends all; a reused refresh token ends all |
| Access token | 15 minutes |
| Session, from its sign-in | 12 hours at most, however often renewed |
| Inactivity | 30 minutes without a renewal ends it (`RefreshTokenService`); in the browser, 30 minutes without input signs out, with a warning 2 minutes before (`IdleSessionGuard`) |
| Sign-in from another kind of device | its owner is emailed: device, address, time, what to do (`NewDeviceMonitor`); not on the first sign-in recorded, nor for the public demo |

## Operations that ask for the password again

Answered 428 without a fresh confirmation: the browser asks for the password
(and the 2FA code, if the account uses one), `POST /auth/reauth` returns a
token valid 5 minutes, and the request is repeated with it
(`ReauthenticationService`, `ReauthenticationTest`). A wrong password counts
towards the same lock as the sign-in.

- changing an account's email (where its password resets are sent);
- setting another user's password;
- exporting the organization's data;
- turning 2FA off (until 2026-11-24 a code alone, with no limit on wrong
  ones and the login's code usable again: security audit).

Already confirmed in their own way: changing one's own password (the current
one), deleting the organization (an emailed code).

## Rate limits and budgets

| What | Limit | Keyed by | Code |
|---|---|---|---|
| Sign-in, registration, password reset, re-authentication | 5 a minute | address | `RateLimitingFilter` |
| Session renewal | 30 a minute | address | `RateLimitingFilter` |
| Changes (POST, PUT, PATCH, DELETE) | 60 a minute | user | `WriteBudgetLimiter` |
| Heavy operations: exports, imports, uploads, file parsing, embeddings | 20 a minute | user | `CostlyOperationLimiter` |
| Language model calls | 5 a minute, one at a time | user | `CostlyOperationLimiter`, `MlProxyService` |
| Wrong passwords before a 15-minute lock | 5 | account | `LoginLockout` |
| Registration codes sent | one a minute, 5 in 24 hours | email address | `OrganizationRegistrationService` |
| Deletion codes sent | one a minute | organization | `OrganizationDeletionService` |
| Wrong registration or deletion codes | 5, then a new code is needed | the pending request | the two services above |

Every refusal answers 429 (the lock: the usual sign-in failure) and is
recorded as a security event, reported weekly.

## Sizes

| What | Limit |
|---|---|
| An uploaded file | 10 MB |
| A CSV/Excel import | 5000 rows |
| OCR of a scanned PDF | the first 10 pages |
| A page of results | 1000 rows |
| A search text | 255 characters |
| Every other text field and parameter | its own bound (`InputValidationGuardTest`) |

## Time limits

| What | Lasts |
|---|---|
| Access token | 15 minutes |
| Session (refresh token) | 12 hours from the sign-in, ended by 30 minutes of inactivity |
| Re-authentication | 5 minutes |
| Password reset link | 30 minutes, one use |
| Registration and deletion codes | 15 minutes |
| Organization deletion | 30 days to cancel it |
| Audit log | 180 days |
| SEPA payment records | 2 years |
