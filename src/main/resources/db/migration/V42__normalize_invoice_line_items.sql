-- Invoice line items lived as a JSON blob (line_items_json), populated by
-- serializing List<InvoiceLineItemDTO> with the app-wide Jackson
-- ObjectMapper -- present but not queryable/joinable at the DB level.
-- Moves them into a proper child table. JSON_TABLE (MySQL 8, already the
-- version in use) explodes each invoice's JSON array into rows in one
-- pass; no Java-based migration needed.

CREATE TABLE invoice_line_items (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id     BIGINT         NOT NULL,
    line_number    INT,
    description    VARCHAR(1000),
    quantity       DECIMAL(15,4),
    unit_of_measure VARCHAR(20),
    unit_price     DECIMAL(15,4),
    total_price    DECIMAL(15,2),
    vat_rate       DECIMAL(5,2),
    CONSTRAINT fk_invoice_line_item_invoice
        FOREIGN KEY (invoice_id) REFERENCES electronic_invoices(id) ON DELETE CASCADE,
    INDEX idx_invoice_line_item_invoice_id (invoice_id)
);

INSERT INTO invoice_line_items
    (invoice_id, line_number, description, quantity, unit_of_measure, unit_price, total_price, vat_rate)
SELECT
    ei.id,
    jt.line_number,
    jt.description,
    jt.quantity,
    jt.unit_of_measure,
    jt.unit_price,
    jt.total_price,
    jt.vat_rate
FROM electronic_invoices ei,
JSON_TABLE(
    ei.line_items_json,
    '$[*]' COLUMNS (
        line_number     INT           PATH '$.lineNumber',
        description     VARCHAR(1000) PATH '$.description',
        quantity        DECIMAL(15,4) PATH '$.quantity',
        unit_of_measure VARCHAR(20)   PATH '$.unitOfMeasure',
        unit_price      DECIMAL(15,4) PATH '$.unitPrice',
        total_price     DECIMAL(15,2) PATH '$.totalPrice',
        vat_rate        DECIMAL(5,2)  PATH '$.vatRate'
    )
) AS jt
WHERE ei.line_items_json IS NOT NULL AND ei.line_items_json != '';

-- Schema now matches the updated ElectronicInvoice/InvoiceLineItem entity
-- mapping from the moment this migration applies -- no transition period
-- with both storage forms, so the column can go in the same migration.
ALTER TABLE electronic_invoices DROP COLUMN line_items_json;
