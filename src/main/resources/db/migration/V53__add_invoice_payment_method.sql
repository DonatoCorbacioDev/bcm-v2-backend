-- How the invoice is to be paid, as FatturaPA states it (DatiPagamento >
-- DettaglioPagamento > ModalitaPagamento: MP05 bank transfer, MP12 RiBa,
-- MP19-MP21 SEPA direct debit, MP08 card, ...). Only a bank transfer is paid
-- by a SEPA file from BCM; with RiBa or a direct debit the supplier collects
-- the money itself, and paying it by transfer too would pay it twice.
-- Null when the invoice states no payment terms; existing invoices are filled
-- in at startup from their stored XML (InvoicePaymentMethodBackfill).

ALTER TABLE electronic_invoices
    ADD COLUMN payment_method VARCHAR(4) NULL AFTER payment_due_date;
