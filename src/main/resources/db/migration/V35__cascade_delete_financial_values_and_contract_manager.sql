-- financial_values.contract_id and contract_manager.contract_id had no
-- ON DELETE clause (RESTRICT by default), and ContractService.deleteContract()
-- never cleans either up before deleting the contract — same bug shape as
-- contract_id on contract_history (V33) and modified_by (V34), just on two
-- more child tables that were missed. Any contract with at least one
-- financial value (a core, prominently-featured part of the app) or an
-- assigned collaborator manager could never be deleted, failing with the
-- same misleading "duplicate value" 409. Unlike modified_by, both tables
-- hold data that belongs to the contract itself (not a reference to a
-- person whose audit trail must survive), so CASCADE is correct here.
ALTER TABLE financial_values
    DROP FOREIGN KEY financial_values_ibfk_3,
    ADD CONSTRAINT fk_fv_contract FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE;

ALTER TABLE contract_manager
    DROP FOREIGN KEY contract_manager_ibfk_1,
    ADD CONSTRAINT fk_cm_contract FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE;
