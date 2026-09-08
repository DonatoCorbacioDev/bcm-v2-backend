-- Links an electronic invoice to the FinancialValues row it corresponds to,
-- with a confidence score computed by InvoiceMatchingService and a status
-- that starts as a suggestion and only becomes authoritative once a human
-- confirms it (see that service's Javadoc for the exact scoring rule).
ALTER TABLE electronic_invoices
    ADD COLUMN matched_financial_value_id BIGINT NULL,
    ADD COLUMN match_confidence DOUBLE NULL,
    ADD COLUMN match_status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
    ADD COLUMN matched_at DATETIME(6) NULL,
    ADD COLUMN matched_by_user_id BIGINT NULL,
    ADD CONSTRAINT fk_invoice_matched_fv FOREIGN KEY (matched_financial_value_id)
        REFERENCES financial_values(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_invoice_matched_by FOREIGN KEY (matched_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;
