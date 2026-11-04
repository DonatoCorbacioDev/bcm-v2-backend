-- Tacit renewal terms of a contract. With a tacit renewal, what matters is not
-- the end date but the last day to give notice (end_date - notice_days): miss
-- it and the contract renews for renewal_months more. All three are NULL for
-- every existing contract on purpose: BCM must not invent contract terms, the
-- user states them.
--   auto_renew      NULL = not stated, FALSE = ends at end_date, TRUE = renews tacitly
--   notice_days     days of notice the contract requires
--   renewal_months  length of each renewal
ALTER TABLE contracts
    ADD COLUMN auto_renew BOOLEAN NULL,
    ADD COLUMN notice_days INT NULL,
    ADD COLUMN renewal_months INT NULL,
    ADD CONSTRAINT chk_contracts_notice_days CHECK (notice_days IS NULL OR notice_days BETWEEN 0 AND 3650),
    ADD CONSTRAINT chk_contracts_renewal_months CHECK (renewal_months IS NULL OR renewal_months BETWEEN 1 AND 120);
