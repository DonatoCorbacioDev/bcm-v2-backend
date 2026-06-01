CREATE TABLE contract_documents (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id  BIGINT        NOT NULL,
    s3_key       VARCHAR(512)  NOT NULL UNIQUE,
    file_name    VARCHAR(255)  NOT NULL,
    file_size    BIGINT        NOT NULL,
    content_type VARCHAR(100)  NOT NULL DEFAULT 'application/pdf',
    org_id       BIGINT,
    uploaded_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_doc_contract
        FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    INDEX idx_doc_contract_id (contract_id),
    INDEX idx_doc_org_id      (org_id)
);
