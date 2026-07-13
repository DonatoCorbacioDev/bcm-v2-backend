-- The Java ContractStatus enum has included DRAFT since ContractTemplate was
-- added, but the native MySQL ENUM columns were never widened to match —
-- inserting/updating a contract to DRAFT silently truncated the column
-- (MySQL's default behavior for an out-of-range ENUM value) instead of
-- storing it. The contract approval workflow's entry point is exactly this
-- transition, so this is fixed here rather than left latent.
ALTER TABLE contracts MODIFY COLUMN status ENUM('ACTIVE', 'EXPIRED', 'CANCELLED', 'DRAFT') NOT NULL;
ALTER TABLE contract_history MODIFY COLUMN previous_status ENUM('ACTIVE', 'EXPIRED', 'CANCELLED', 'DRAFT');
ALTER TABLE contract_history MODIFY COLUMN new_status ENUM('ACTIVE', 'EXPIRED', 'CANCELLED', 'DRAFT');
