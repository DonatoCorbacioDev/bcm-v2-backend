-- Verified IBAN/BIC a counterparty is known to pay into, separate from the
-- IBAN read off each incoming electronic invoice. Lets SepaPaymentService
-- reject a SEPA batch whose invoice IBAN differs from the one already
-- trusted for that supplier (invoice-IBAN-swap fraud), instead of blindly
-- paying whatever the latest invoice XML says. NULL until a SEPA batch is
-- first generated for that counterparty, or until an admin confirms a
-- legitimate IBAN change via the dedicated endpoint.
ALTER TABLE counterparties
    ADD COLUMN verified_iban VARCHAR(34),
    ADD COLUMN verified_bic VARCHAR(11);
