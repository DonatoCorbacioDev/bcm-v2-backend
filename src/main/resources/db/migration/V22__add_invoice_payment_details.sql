ALTER TABLE electronic_invoices
    ADD COLUMN supplier_iban    VARCHAR(34),
    ADD COLUMN supplier_bic     VARCHAR(11),
    ADD COLUMN payment_due_date DATE;
