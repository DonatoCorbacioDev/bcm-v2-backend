-- V19__translate_reference_data_to_italian.sql
-- V2 seeded business_areas/financial_types in English. The product's UI and
-- target market are Italian, so translate the reference data itself (not just
-- labels around it) — matched by the original English name, so any org that
-- has since renamed these rows keeps its own value.

UPDATE business_areas SET name = 'Servizi IT', description = 'Sviluppo software e tecnologia dell''informazione' WHERE name = 'IT Services';
UPDATE business_areas SET name = 'Finanza', description = 'Consulenza finanziaria e contabilità' WHERE name = 'Finance';
UPDATE business_areas SET name = 'Costruzioni', description = 'Sviluppo edilizio e infrastrutturale' WHERE name = 'Construction';
UPDATE business_areas SET name = 'Legale', description = 'Affari legali e conformità normativa' WHERE name = 'Legal';
UPDATE business_areas SET name = 'Operazioni', description = 'Operazioni aziendali e logistica' WHERE name = 'Operations';

UPDATE financial_types SET name = 'Ricavi', description = 'Ricavi dalla vendita di prodotti o servizi' WHERE name = 'SALES';
UPDATE financial_types SET name = 'Costi', description = 'Spese operative e costi aziendali' WHERE name = 'COSTS';
UPDATE financial_types SET name = 'Non classificato', description = 'Non classificato o non rilevante ai fini del reporting finanziario' WHERE name = 'NR';
