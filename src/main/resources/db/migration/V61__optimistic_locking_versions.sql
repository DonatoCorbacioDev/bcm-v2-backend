-- A version number on the rows several people can change at the same time
-- (OWASP Database Security, concurrent updates; JPA @Version): every update
-- checks the version it read and increments it, so of two overlapping changes
-- the second is refused (409) instead of silently overwriting the first.
--   contracts:      edited, and moved through review and approval, by several users
--   counterparties: edited by several users, including the verified IBAN
--   organizations:  settings edited by admins, plan changed by Stripe's webhook
-- Existing rows start at 0.
ALTER TABLE contracts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE counterparties ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE organizations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
