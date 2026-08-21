-- contract_history was the only child table of contracts without
-- ON DELETE CASCADE (every other one added since — contract_workflow_events,
-- contract_documents, electronic_invoices, sepa_payment_batches,
-- risk_feedback — has it). Any contract that ever had a status change
-- (routine, via the approval workflow or a manual edit) accumulates
-- contract_history rows and becomes permanently undeletable, failing with a
-- misleading "duplicate value" 409 instead of the real FK conflict.
ALTER TABLE contract_history
    DROP FOREIGN KEY contract_history_ibfk_1,
    ADD CONSTRAINT fk_ch_contract FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE;
