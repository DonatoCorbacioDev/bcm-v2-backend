CREATE TABLE sepa_payment_batches (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    contract_id          BIGINT NOT NULL,
    org_id               BIGINT,
    message_id           VARCHAR(35) NOT NULL,
    execution_date       DATE NOT NULL,
    total_amount         DECIMAL(15,2) NOT NULL,
    currency             VARCHAR(3) NOT NULL,
    number_of_transactions INT NOT NULL,
    storage_path         VARCHAR(512) NOT NULL UNIQUE,
    file_name            VARCHAR(255) NOT NULL,
    created_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_spb_contract FOREIGN KEY (contract_id) REFERENCES contracts(id)     ON DELETE CASCADE,
    CONSTRAINT fk_spb_org      FOREIGN KEY (org_id)       REFERENCES organizations(id) ON DELETE CASCADE,
    INDEX idx_spb_contract (contract_id),
    INDEX idx_spb_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE electronic_invoices
    ADD COLUMN sepa_batch_id BIGINT,
    ADD CONSTRAINT fk_invoice_sepa_batch FOREIGN KEY (sepa_batch_id) REFERENCES sepa_payment_batches(id) ON DELETE SET NULL,
    ADD INDEX idx_invoice_sepa_batch_id (sepa_batch_id);
