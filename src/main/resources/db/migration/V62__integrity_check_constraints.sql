-- Integrity rules enforced by MySQL itself (OWASP Database Security, data
-- integrity), as a second barrier behind the API's validation: the same
-- limits, so a row that reaches the database another way (a script, a bug,
-- a future endpoint) cannot break them either. Production held no row
-- violating any of them when this was written (checked 2026-11-20).
-- Invoice totals are left free on purpose: credit notes (TD04) are negative.
ALTER TABLE contracts
    ADD CONSTRAINT chk_contracts_dates CHECK (end_date IS NULL OR end_date >= start_date),
    ADD CONSTRAINT chk_contracts_annual_value CHECK (annual_value IS NULL OR annual_value >= 0);

ALTER TABLE budgets
    ADD CONSTRAINT chk_budgets_target_amount CHECK (target_amount >= 0),
    ADD CONSTRAINT chk_budgets_year CHECK (year_value BETWEEN 2000 AND 2100);

ALTER TABLE financial_values
    ADD CONSTRAINT chk_financial_values_month CHECK (month_value BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_financial_values_year CHECK (year_value BETWEEN 2000 AND 2100);

ALTER TABLE contract_documents
    ADD CONSTRAINT chk_contract_documents_file_size CHECK (file_size >= 0),
    ADD CONSTRAINT chk_contract_documents_version_number CHECK (version_number >= 1);

ALTER TABLE electronic_invoices
    ADD CONSTRAINT chk_electronic_invoices_file_size CHECK (file_size >= 0),
    ADD CONSTRAINT chk_electronic_invoices_match_confidence CHECK (match_confidence IS NULL OR match_confidence BETWEEN 0 AND 1);

ALTER TABLE users
    ADD CONSTRAINT chk_users_failed_login_attempts CHECK (failed_login_attempts >= 0);
