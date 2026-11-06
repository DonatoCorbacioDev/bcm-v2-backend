-- Organizations registered after the V2 seed started with no financial types,
-- so a contract's annual value couldn't be entered until someone created one
-- in the settings. New organizations now get "Costi" and "Ricavi" at
-- registration (OrganizationService); this gives the same two to existing
-- organizations that have none. Organizations with any type are left alone.
-- One statement: the NOT EXISTS is evaluated against the table as it was
-- before the insert, so each organization gets both rows or neither.

INSERT INTO financial_types (name, description, category, organization_id)
SELECT d.name, d.description, d.category, o.id
FROM organizations o
CROSS JOIN (
    SELECT 'Costi' AS name, 'Spese per fornitori e servizi' AS description, 'COST' AS category
    UNION ALL
    SELECT 'Ricavi', 'Ricavi dalla vendita di prodotti o servizi', 'REVENUE'
) d
WHERE NOT EXISTS (SELECT 1 FROM financial_types ft WHERE ft.organization_id = o.id);
