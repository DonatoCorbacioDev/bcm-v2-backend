CREATE TABLE contract_templates (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    default_status    VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    default_duration_days INT,
    business_area_id  BIGINT,
    default_manager_id BIGINT,
    auto_renew  BOOLEAN NOT NULL DEFAULT FALSE,
    notification_days INT,
    org_id      BIGINT NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ct_business_area  FOREIGN KEY (business_area_id)  REFERENCES business_areas(id) ON DELETE SET NULL,
    CONSTRAINT fk_ct_default_manager FOREIGN KEY (default_manager_id) REFERENCES managers(id)       ON DELETE SET NULL,
    CONSTRAINT fk_ct_org             FOREIGN KEY (org_id)             REFERENCES organizations(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
