-- A version number on electronic invoices too (as V61 for contracts,
-- counterparties and organizations; JPA @Version). An invoice is written by
-- the SEPA payment (its batch), by its payment details, by the matching with
-- financial values and by backfills: each update rewrites the whole row, so
-- an edit made from a copy read before a SEPA payment committed would write
-- the batch back to NULL, and the invoice could be paid in a second batch
-- (security audit 2026-11-24). With the version, that edit is refused (409).
-- Existing rows start at 0.
ALTER TABLE electronic_invoices ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
