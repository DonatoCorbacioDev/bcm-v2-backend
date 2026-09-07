-- Optional financial terms on a contract (financial_type_id + annual_value +
-- billing_frequency): when all three are set, ContractFinancialGenerationService
-- auto-generates the contract's financial_values rows instead of requiring
-- them to be entered one at a time. All three columns are nullable so every
-- existing contract keeps behaving exactly as before (opt-in, not required).
ALTER TABLE contracts
    ADD COLUMN financial_type_id BIGINT NULL,
    ADD COLUMN annual_value DOUBLE NULL,
    ADD COLUMN billing_frequency VARCHAR(10) NULL,
    ADD CONSTRAINT fk_contracts_financial_type FOREIGN KEY (financial_type_id) REFERENCES financial_types(id);

-- `source` distinguishes a manually-entered row from one produced by the
-- generation service — without it, a regenerate can't tell a user's edit
-- apart from its own earlier output and would silently overwrite it.
-- Every existing row becomes MANUAL, which is correct: nothing generated
-- anything before this migration.
--
-- The new unique constraint below is new -- nothing enforced this before,
-- so two manual rows for the same contract/type/month/year were previously
-- legal. This ALTER will fail if any such duplicates already exist in your
-- data; run this first and resolve any hits before deploying:
--   SELECT contract_id, financial_type_id, month_value, year_value, COUNT(*)
--   FROM financial_values
--   GROUP BY contract_id, financial_type_id, month_value, year_value
--   HAVING COUNT(*) > 1;
ALTER TABLE financial_values
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    ADD CONSTRAINT uq_fv_contract_type_month_year UNIQUE (contract_id, financial_type_id, month_value, year_value);
