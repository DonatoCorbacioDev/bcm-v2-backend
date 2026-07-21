-- Budget aziendale: a yearly spending/revenue target per business area,
-- compared against the sum of FinancialValues already tracked per contract.
--
-- financial_types had no structured Revenue/Cost distinction (free-text
-- name only), needed to know which FinancialValues to sum into a budget.
ALTER TABLE financial_types ADD COLUMN category VARCHAR(10) NOT NULL DEFAULT 'COST';
UPDATE financial_types
SET category = 'REVENUE'
WHERE LOWER(name) LIKE '%ricav%' OR LOWER(name) LIKE '%vendit%' OR LOWER(name) LIKE '%sale%' OR LOWER(name) LIKE '%revenue%';
ALTER TABLE financial_types ALTER COLUMN category DROP DEFAULT;

CREATE TABLE budgets (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    business_area_id  BIGINT NOT NULL,
    category          VARCHAR(10) NOT NULL,
    year_value        INT NOT NULL,
    target_amount     DOUBLE NOT NULL,
    organization_id   BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_budget_area FOREIGN KEY (business_area_id) REFERENCES business_areas(id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_org  FOREIGN KEY (organization_id)  REFERENCES organizations(id)   ON DELETE CASCADE,
    CONSTRAINT uq_budget_area_category_year_org UNIQUE (business_area_id, category, year_value, organization_id),
    INDEX idx_budget_org (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
