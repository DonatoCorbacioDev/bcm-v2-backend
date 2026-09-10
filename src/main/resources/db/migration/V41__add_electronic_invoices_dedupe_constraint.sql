-- Nothing previously prevented uploading the exact same FatturaPA invoice
-- twice (even onto the wrong contract): storage_path is unique but is an
-- app-generated UUID path, not derived from invoice content. The
-- application now checks for an existing match before insert; this
-- constraint is the safety net for concurrent uploads racing past that
-- check. NULLs (e.g. suppliers with no VAT number) are each treated as
-- distinct by MySQL, so they never collide with one another.
ALTER TABLE electronic_invoices
    ADD CONSTRAINT uq_electronic_invoices_dedupe
        UNIQUE (org_id, supplier_vat_number, invoice_number, document_type);
