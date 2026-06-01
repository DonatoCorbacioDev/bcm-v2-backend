CREATE TABLE audit_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    action      VARCHAR(20)   NOT NULL,
    entity_type VARCHAR(100)  NOT NULL,
    entity_id   BIGINT,
    username    VARCHAR(150),
    org_id      BIGINT,
    timestamp   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    details     VARCHAR(500),
    INDEX idx_audit_logs_username  (username),
    INDEX idx_audit_logs_timestamp (timestamp),
    INDEX idx_audit_logs_org_id    (org_id)
);
