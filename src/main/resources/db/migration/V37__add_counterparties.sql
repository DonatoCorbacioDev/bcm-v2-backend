CREATE TABLE counterparties (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    name             VARCHAR(255) NOT NULL,
    type             VARCHAR(10) NOT NULL,
    vat_number       VARCHAR(50),
    tax_code         VARCHAR(50),
    address          VARCHAR(500),
    contact_name     VARCHAR(255),
    contact_email    VARCHAR(255),
    contact_phone    VARCHAR(50),
    notes            TEXT,
    organization_id  BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_cp_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT uq_cp_name_org UNIQUE (name, organization_id),
    INDEX idx_cp_org (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill: one Counterparty per distinct (organization_id, customer_name)
-- already present in existing contracts, type CUSTOMER by default (mirrors
-- the field's historical name; editable afterwards from the UI). Contracts
-- with a NULL organization_id (pre-multi-tenancy legacy rows, if any survive)
-- are intentionally left unmigrated here -- verify with a count query before
-- running this in an environment where that's possible.
INSERT INTO counterparties (name, type, organization_id)
SELECT DISTINCT c.customer_name, 'CUSTOMER', c.organization_id
FROM contracts c
WHERE c.organization_id IS NOT NULL;

ALTER TABLE contracts ADD COLUMN counterparty_id BIGINT;

UPDATE contracts c
JOIN counterparties cp
  ON cp.name = c.customer_name AND cp.organization_id = c.organization_id
SET c.counterparty_id = cp.id;

ALTER TABLE contracts MODIFY COLUMN counterparty_id BIGINT NOT NULL;
ALTER TABLE contracts ADD CONSTRAINT fk_ctr_counterparty
  FOREIGN KEY (counterparty_id) REFERENCES counterparties(id);
ALTER TABLE contracts ADD INDEX idx_contracts_counterparty_id (counterparty_id);
ALTER TABLE contracts DROP COLUMN customer_name;
