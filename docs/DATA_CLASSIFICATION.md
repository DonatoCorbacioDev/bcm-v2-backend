# Data classification

Every kind of data BCM holds, by how much harm its disclosure or change
would do, and how each level is protected (OWASP ASVS 5.0, V14.1). The
personal data among it, and why it is processed, are in `GDPR.md`; the rules
on how long it is kept are in `GDPR.md` (section 5) and `BUSINESS_RULES.md`.

## Levels

| Level | Meaning | Protection |
|---|---|---|
| **Secret** | Opens other data or other accounts: whoever has it acts as the system or as a user | Never stored in a readable form, or only encrypted with a key kept elsewhere; never in a log, an answer, a URL or the repositories; rotated when exposed |
| **Confidential** | An organization's business, or a person's: harm if read by another organization or the public | Only to the organization's own users, by role (BOLA matrix); TLS everywhere it travels; in encrypted backups only; never in logs; exported only by an administrator after re-entering the password |
| **Internal** | Describes how BCM works or is used, not anyone's business | Not shown to the public; may be in logs, without secrets or confidential values |
| **Public** | Meant to be read by anyone | No restriction |

## What is where

| Data | Level | How it is kept |
|---|---|---|
| Passwords | Secret | BCrypt hash only (`users.password_hash`) |
| Two-factor secrets | Secret | AES-256-GCM with `TOTP_ENCRYPTION_KEY` (`users.totp_secret_encrypted`) |
| Recovery codes | Secret | BCrypt hash only |
| Refresh, reset and invitation tokens | Secret | SHA-256 only; the value exists only in the cookie or the link |
| Access and re-authentication tokens | Secret | In the browser's memory only, never stored; 15 and 5 minutes |
| Calendar feed token | Secret | In the feed's URL by necessity (calendar apps send no header); masked in every log line, regenerable |
| Keys and passwords of the services (JWT, TOTP, MySQL, Redis, SMTP, Stripe, ML) | Secret | Docker secrets on the VM, 700/444; the recovery bundle age-encrypted to a key off the VM |
| IBANs, verified IBANs, SEPA payments | Confidential | Per organization; an IBAN changed blocks the payment; SEPA records 2 years |
| Contracts, financial values, budgets, documents, invoices | Confidential | Per organization, by role; files under a directory per organization, downloaded as attachments only |
| Counterparties, managers' names, emails and phones | Confidential (personal data) | Per organization; an email change asks for the password again |
| Audit log | Confidential | Per organization, administrators only; rows cannot be changed; 180 days |
| IP addresses and devices of sign-ins | Confidential (personal data) | Audit log and the journal, 180 days; the last device's kind only on the account |
| Application logs | Internal | The VM's journal, 180 days or 2 GB, and a nightly age-encrypted copy off the VM; no secret or confidential value (tested) |
| Metrics, health | Internal | Health public, metrics to the VM's private addresses only |
| ML risk scores and forecasts | Confidential | Computed per organization, from its own contracts; the language model runs on the VM, no data leaves it |
| Plans, terms versions, legal pages | Public | |

## Where data may leave

Only to what `OutboundRequestsTest` lists, each for its reason: the ML service
and Ollama on the same VM, ClamAV (files to scan), Have I Been Pwned (5 hex
characters of a password's SHA-1, nothing else), and, outside the backend,
the SMTP provider (the emails BCM sends) and Stripe (billing). Backups and
logs go to the Oracle bucket encrypted with a key the VM does not have.

## Data in URLs

Kept out of URLs, except where a link must carry it: the calendar feed (see
above), the password reset and invitation links (single use, 30 minutes and
the invitation's own validity, stored as SHA-256, sent with
`Referrer-Policy: strict-origin-when-cross-origin`, so no other site receives
them). No other token, password or identifier of a person goes into a URL.
