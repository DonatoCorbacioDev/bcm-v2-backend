CREATE TABLE risk_feedback (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    contract_id   BIGINT NOT NULL,
    submitted_by  BIGINT NOT NULL,
    org_id        BIGINT NOT NULL,
    risk_score    DOUBLE NOT NULL,
    risk_level    VARCHAR(10) NOT NULL,
    ml_score      DOUBLE,
    ml_level      VARCHAR(10),
    agree         BOOLEAN NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_rf_contract FOREIGN KEY (contract_id)  REFERENCES contracts(id)     ON DELETE CASCADE,
    CONSTRAINT fk_rf_user     FOREIGN KEY (submitted_by) REFERENCES users(id)         ON DELETE CASCADE,
    CONSTRAINT fk_rf_org      FOREIGN KEY (org_id)       REFERENCES organizations(id) ON DELETE CASCADE,
    INDEX idx_rf_contract (contract_id),
    INDEX idx_rf_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
