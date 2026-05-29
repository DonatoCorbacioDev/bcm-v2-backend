-- V8__add_organization_id.sql
-- Add organization_id FK to all tenant-scoped tables.
-- Existing data is assigned to the default org (id=1) created in V7.

-- 1. Add nullable columns
ALTER TABLE business_areas   ADD COLUMN organization_id BIGINT;
ALTER TABLE financial_types  ADD COLUMN organization_id BIGINT;
ALTER TABLE managers         ADD COLUMN organization_id BIGINT;
ALTER TABLE contracts        ADD COLUMN organization_id BIGINT;
ALTER TABLE financial_values ADD COLUMN organization_id BIGINT;
ALTER TABLE users            ADD COLUMN organization_id BIGINT;

-- 2. Assign all existing rows to the default organization
UPDATE business_areas   SET organization_id = 1;
UPDATE financial_types  SET organization_id = 1;
UPDATE managers         SET organization_id = 1;
UPDATE contracts        SET organization_id = 1;
UPDATE financial_values SET organization_id = 1;
UPDATE users            SET organization_id = 1;

-- 3. Enforce NOT NULL
ALTER TABLE business_areas   MODIFY COLUMN organization_id BIGINT NOT NULL;
ALTER TABLE financial_types  MODIFY COLUMN organization_id BIGINT NOT NULL;
ALTER TABLE managers         MODIFY COLUMN organization_id BIGINT NOT NULL;
ALTER TABLE contracts        MODIFY COLUMN organization_id BIGINT NOT NULL;
ALTER TABLE financial_values MODIFY COLUMN organization_id BIGINT NOT NULL;
ALTER TABLE users            MODIFY COLUMN organization_id BIGINT NOT NULL;

-- 4. Add FK constraints
ALTER TABLE business_areas   ADD CONSTRAINT fk_ba_org  FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE financial_types  ADD CONSTRAINT fk_ft_org  FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE managers         ADD CONSTRAINT fk_mgr_org FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE contracts        ADD CONSTRAINT fk_ctr_org FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE financial_values ADD CONSTRAINT fk_fv_org  FOREIGN KEY (organization_id) REFERENCES organizations(id);
ALTER TABLE users            ADD CONSTRAINT fk_usr_org FOREIGN KEY (organization_id) REFERENCES organizations(id);

-- 5. Replace global UNIQUE constraints with per-tenant composite ones
-- business_areas.name
ALTER TABLE business_areas  DROP INDEX name;
ALTER TABLE business_areas  ADD  CONSTRAINT uq_ba_name_org  UNIQUE (name, organization_id);

-- financial_types.name
ALTER TABLE financial_types DROP INDEX name;
ALTER TABLE financial_types ADD  CONSTRAINT uq_ft_name_org  UNIQUE (name, organization_id);

-- managers.email
ALTER TABLE managers        DROP INDEX email;
ALTER TABLE managers        ADD  CONSTRAINT uq_mgr_email_org UNIQUE (email, organization_id);

-- contracts.contract_number
ALTER TABLE contracts       DROP INDEX contract_number;
ALTER TABLE contracts       ADD  CONSTRAINT uq_ctr_num_org   UNIQUE (contract_number, organization_id);

-- users.username
ALTER TABLE users           DROP INDEX username;
ALTER TABLE users           ADD  CONSTRAINT uq_usr_name_org  UNIQUE (username, organization_id);
