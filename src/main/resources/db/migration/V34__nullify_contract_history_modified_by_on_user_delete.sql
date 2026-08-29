-- contract_history.modified_by has always been a plain FK to users(id) with
-- no ON DELETE clause (defaults to RESTRICT): any user who ever changed a
-- contract's status (approval workflow, manual edit, or the daily expiration
-- cron, which always attributes the change to an ADMIN) can never be
-- deleted, failing with the same misleading "duplicate value" 409 fixed for
-- contract_id in V33. Unlike contract_id, cascading the delete here would
-- destroy audit history that may be needed for compliance — so the column
-- is nullified instead, keeping the history row (what changed, when) while
-- severing the link to the deleted person. See docs/GDPR.md #5/#9.
ALTER TABLE contract_history
    MODIFY COLUMN modified_by BIGINT NULL,
    DROP FOREIGN KEY contract_history_ibfk_2,
    ADD CONSTRAINT fk_ch_modified_by FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE SET NULL;
