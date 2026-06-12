CREATE TABLE electronic_invoices (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id         BIGINT        NOT NULL,
    storage_path        VARCHAR(512)  NOT NULL UNIQUE,
    file_name           VARCHAR(255)  NOT NULL,
    file_size           BIGINT        NOT NULL,
    content_type        VARCHAR(100)  NOT NULL DEFAULT 'application/xml',
    org_id              BIGINT,
    uploaded_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    supplier_name       VARCHAR(255),
    supplier_vat_number VARCHAR(30),
    document_type       VARCHAR(10),
    invoice_number      VARCHAR(50),
    invoice_date        DATE,
    total_amount        DECIMAL(15,2),
    currency            VARCHAR(3),
    line_items_json     LONGTEXT,
    CONSTRAINT fk_invoice_contract
        FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    INDEX idx_invoice_contract_id (contract_id),
    INDEX idx_invoice_org_id      (org_id)
);
