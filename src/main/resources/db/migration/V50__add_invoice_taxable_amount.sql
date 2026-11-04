-- The taxable amount (imponibile) and VAT of an invoice, from the
-- DatiRiepilogo blocks of the FatturaPA. Contract amounts are IVA esclusa, so
-- comparing them with total_amount (IVA inclusa) made every invoice look ~22%
-- off. NULL for invoices already stored: InvoiceTaxableAmountBackfill re-reads
-- their XML at startup and fills them in.
ALTER TABLE electronic_invoices
    ADD COLUMN taxable_amount DECIMAL(15, 2) NULL,
    ADD COLUMN vat_amount DECIMAL(15, 2) NULL;
