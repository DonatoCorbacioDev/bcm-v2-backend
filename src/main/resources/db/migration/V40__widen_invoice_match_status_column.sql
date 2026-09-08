-- V39 sized match_status as VARCHAR(20), too short for the enum value
-- COUNTERPARTY_MISMATCH (21 characters) -- every invoice upload whose
-- supplier doesn't match the contract's counterparty failed with a MySQL
-- data-truncation error (SQLState 22001), surfaced to the API as a
-- generic 409. Widened with headroom for any future status name.
ALTER TABLE electronic_invoices
    MODIFY COLUMN match_status VARCHAR(30) NOT NULL DEFAULT 'UNMATCHED';
