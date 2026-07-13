ALTER TABLE contracts
    ADD COLUMN workflow_stage VARCHAR(20);

UPDATE contracts SET workflow_stage = 'DRAFT' WHERE status = 'DRAFT';

ALTER TABLE users
    ADD COLUMN can_approve_contracts BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE contract_workflow_events (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    contract_id   BIGINT NOT NULL,
    from_stage    VARCHAR(20),
    to_stage      VARCHAR(20) NOT NULL,
    action        VARCHAR(10) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    comment       TEXT,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_cwe_contract FOREIGN KEY (contract_id)   REFERENCES contracts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cwe_actor    FOREIGN KEY (actor_user_id) REFERENCES users(id)     ON DELETE CASCADE,
    INDEX idx_cwe_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
