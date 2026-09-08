-- ============================================================
-- BCM PUBLIC DEMO SEED — org id 3 ("BCM Demo", renamed here to Nortiva
-- Solutions S.r.l.), run nightly on production by demo-reset.sh.
--
-- Replaces the previous approach (bcm-v2-ml's seed_synthetic_data.py
-- --org-id 3, Faker-generated) entirely: the public "demo" account only
-- ever sees this hand-curated set now, so company/contract names stay
-- coherent and demo-safe instead of random Faker output (e.g. "Miniati,
-- Trotta e Ottino SPA").
--
-- The public demo exposes only the MANAGER account ("demo", manager id 4)
-- — never an admin login (an admin could invite/spam arbitrary email
-- addresses via the real invite flow, or edit other users) — so every
-- hero contract below is assigned to that one manager regardless of its
-- business area; manager-scoped visibility filters by manager_id, not
-- business area, so this doesn't limit which areas can be shown.
--
-- Idempotent: safe to re-run (nightly, or by hand). Unlike
-- seed-test-data.sql (local dev, org 1), which only deletes its own named
-- contract_numbers so it can coexist with a separately-run Faker batch,
-- this script wipes and reseeds ALL of org 3's contracts/financial_values/
-- budgets every run — org 3 no longer has any Faker data to preserve.
--
-- Contains no credentials. The "demo" user's password/TOTP reset stays in
-- demo-reset.sh, done through the real API so it goes through
-- BCryptPasswordEncoder like any normal password change.
--
-- Run: docker exec -i bcm-mysql mysql --default-character-set=utf8mb4
--        -u<user> -p<password> bcm < sql/DML/seed-demo-org.sql
-- ============================================================
USE bcm;

-- ── Organization ──────────────────────────────────────────────
UPDATE organizations SET name = 'Nortiva Solutions S.r.l.', slug = 'nortiva-solutions' WHERE id = 3;

-- ── Manager display name (cosmetic only — the login itself stays "demo")
UPDATE managers SET first_name = 'Marco', last_name = 'Bellini', department = 'Direzione'
WHERE id = 4 AND organization_id = 3;

-- ── Business areas: align org 3's original placeholder set to the same
-- clean, coherent set used for the Nortiva brand elsewhere. Renamed in
-- place (never deleted) so existing budgets/history keep their FK intact.
-- Drops "Facility" (no hero scenario needs it) in favor of "Legale", and
-- adds "Finanza" (needed for the expired-contract and workflow heroes).
UPDATE business_areas SET name = 'Servizi IT', description = 'Sviluppo software e infrastruttura IT'
WHERE id = 4 AND organization_id = 3;
UPDATE business_areas SET name = 'Operazioni', description = 'Operazioni aziendali e logistica'
WHERE id = 5 AND organization_id = 3;
UPDATE business_areas SET name = 'Legale', description = 'Affari legali e conformità normativa'
WHERE id = 6 AND organization_id = 3;
INSERT INTO business_areas (name, description, organization_id)
SELECT 'Finanza', 'Amministrazione, finanza e controllo', 3
WHERE NOT EXISTS (SELECT 1 FROM business_areas WHERE name = 'Finanza' AND organization_id = 3);

-- financial_types (Ricavi/Costi) already exist for org 3 and are already
-- correctly categorized (V30) — nothing to do here.

-- ── Reset: wipe every existing contract for org 3 and its financial data.
-- (contract_documents, electronic_invoices, risk_feedback,
--  sepa_payment_batches, contract_workflow_events all cascade
--  automatically via ON DELETE CASCADE.)
DELETE fv FROM financial_values fv JOIN contracts c ON fv.contract_id = c.id WHERE c.organization_id = 3;
DELETE cm FROM contract_manager cm JOIN contracts c ON cm.contract_id = c.id WHERE c.organization_id = 3;
DELETE ch FROM contract_history ch JOIN contracts c ON ch.contract_id = c.id WHERE c.organization_id = 3;
DELETE FROM budgets WHERE organization_id = 3;
DELETE FROM contracts WHERE organization_id = 3;

-- ── Counterparties ───────────────────────────────────────────
-- All invented company names in a consistent Italian register (no real
-- company). Same set as the local Nortiva hero dataset minus the
-- construction-industry one, since "Costruzioni" isn't part of org 3's
-- (deliberately smaller) business-area set.
INSERT INTO counterparties (name, type, organization_id)
SELECT 'Solaris Informatica S.p.A.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Solaris Informatica S.p.A.' AND organization_id = 3);

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Fintera Consulting S.r.l.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Fintera Consulting S.r.l.' AND organization_id = 3);

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Vertice Legal S.r.l.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Vertice Legal S.r.l.' AND organization_id = 3);

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Trasporti Meridiana S.r.l.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Trasporti Meridiana S.r.l.' AND organization_id = 3);

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Cyberdue Security S.r.l.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Cyberdue Security S.r.l.' AND organization_id = 3);

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Manifattura Orion S.p.A.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Manifattura Orion S.p.A.' AND organization_id = 3);

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Nexora Technologies S.r.l.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Nexora Technologies S.r.l.' AND organization_id = 3);

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Ferramenta Bassano S.r.l.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Ferramenta Bassano S.r.l.' AND organization_id = 3);

-- ── Contracts ─────────────────────────────────────────────────
-- All assigned to manager id 4 (the public "demo" login) — role-based
-- visibility scopes a MANAGER to their own contracts only, so this is the
-- only way every hero scenario is actually visible to a site visitor.

-- 1) Flagship: urgent renewal (12 days) AND the financial-terms
-- auto-generation demo. "Rigenera valori finanziari" is ADMIN-only
-- (page.tsx: `if (!isAdmin || !hasFinancialTerms) return null;`) and the
-- public demo deliberately exposes no admin login (see header), so a
-- visitor can never click it themselves. The 3 rows inserted below are
-- pre-computed with the exact same math the service would apply —
-- 60000/QUARTERLY = 15000 every 3 months from start_date — and tagged
-- source=GENERATED, so the Financials tab still shows the feature's real
-- output (with its "Generato" badge) even without a live trigger.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id, financial_type_id, annual_value, billing_frequency)
SELECT (SELECT id FROM counterparties WHERE name='Vertice Legal S.r.l.' AND organization_id=3),
       'NRT-2024-004', 'WBS-LEG-001', 'Audit di Conformità',
       (SELECT id FROM business_areas WHERE name='Legale' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 8 MONTH), DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'ACTIVE', 3,
       (SELECT id FROM financial_types WHERE name='Ricavi' AND organization_id=3), 60000, 'QUARTERLY';

-- 2) Expired, with 12 months of financial history ending at its own
-- end_date (4 months ago), none after — matches its status.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Fintera Consulting S.r.l.' AND organization_id=3),
       'NRT-2024-002', 'WBS-FIN-001', 'Automazione Reporting Finanziario',
       (SELECT id FROM business_areas WHERE name='Finanza' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 16 MONTH), DATE_SUB(CURDATE(), INTERVAL 4 MONTH), 'EXPIRED', 3;

-- 3) Cancelled.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Cyberdue Security S.r.l.' AND organization_id=3),
       'NRT-2024-006', 'WBS-IT-002', 'Aggiornamento Sicurezza Informatica',
       (SELECT id FROM business_areas WHERE name='Servizi IT' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 16 MONTH), DATE_SUB(CURDATE(), INTERVAL 10 MONTH), 'CANCELLED', 3;

-- 4) Second near-expiry (25 days), so the "scadenze imminenti" widget has
-- more than one row during the demo.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Manifattura Orion S.p.A.' AND organization_id=3),
       'NRT-2025-002', 'WBS-OPS-002', 'Trasformazione Digitale',
       (SELECT id FROM business_areas WHERE name='Operazioni' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 9 MONTH), DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'ACTIVE', 3;

-- 5) Long-running IT contract, 18 months of steady-growth financial
-- history for a chart with real shape on its detail page.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Solaris Informatica S.p.A.' AND organization_id=3),
       'NRT-2024-001', 'WBS-IT-001', 'Migrazione Cloud Fase 1',
       (SELECT id FROM business_areas WHERE name='Servizi IT' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 30 MONTH), DATE_ADD(CURDATE(), INTERVAL 5 MONTH), 'ACTIVE', 3;

-- 6) Active operations contract, 10 months of steady financial history.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Trasporti Meridiana S.r.l.' AND organization_id=3),
       'NRT-2024-005', 'WBS-OPS-001', 'Ottimizzazione Supply Chain',
       (SELECT id FROM business_areas WHERE name='Operazioni' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 10 MONTH), DATE_ADD(CURDATE(), INTERVAL 60 DAY), 'ACTIVE', 3;

-- 7) Recently started, long horizon.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Nexora Technologies S.r.l.' AND organization_id=3),
       'NRT-2026-001', 'WBS-IT-003', 'Sviluppo Piattaforma AI',
       (SELECT id FROM business_areas WHERE name='Servizi IT' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 1 MONTH), DATE_ADD(CURDATE(), INTERVAL 24 MONTH), 'ACTIVE', 3;

-- 8) Workflow demo: DRAFT, sitting in IN_REVIEW, submitted by the "demo"
-- user. No public login can approve it (the public demo is deliberately
-- MANAGER-only — see header), so this shows the workflow UI/history
-- exists without exposing an admin/approver account publicly.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, workflow_stage, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Ferramenta Bassano S.r.l.' AND organization_id=3),
       'NRT-2026-002', 'WBS-FIN-002', 'Fornitura Software Gestionale',
       (SELECT id FROM business_areas WHERE name='Finanza' AND organization_id=3),
       4, DATE_ADD(CURDATE(), INTERVAL 15 DAY), NULL, 'DRAFT', 'IN_REVIEW', 3;

INSERT INTO contract_workflow_events (contract_id, from_stage, to_stage, action, actor_user_id, comment, created_at)
SELECT (SELECT id FROM contracts WHERE contract_number = 'NRT-2026-002' AND organization_id = 3),
       'DRAFT', 'IN_REVIEW', 'SUBMIT',
       (SELECT id FROM users WHERE username = 'demo' AND organization_id = 3),
       'Inviato per revisione al reparto Finanza',
       NOW() - INTERVAL 2 DAY;

-- ── contract_manager (primary manager assignments) ────────────
INSERT INTO contract_manager (contract_id, manager_id)
SELECT c.id, c.manager_id FROM contracts c
WHERE c.organization_id = 3 AND c.contract_number LIKE 'NRT-%';

-- ── Orphaned counterparties cleanup ─────────────────────────────
-- Previous nights' Faker-generated counterparties (seed_synthetic_data.py)
-- are never referenced by any contract any more (all org 3 contracts were
-- just wiped and recreated above) but were never deleted themselves --
-- counterparties are never removed by a contract delete (no cascade the
-- other way). Left alone, these pile up night after night and reappear as
-- messy names ("Bondumier s.r.l.", "Manunta-Pozzecco Group"...) on the
-- Controparti page. Safe to delete: counterparties has no other inbound FK
-- besides contracts.counterparty_id (checked against the schema).
DELETE FROM counterparties
WHERE organization_id = 3
  AND id NOT IN (SELECT DISTINCT counterparty_id FROM contracts WHERE organization_id = 3 AND counterparty_id IS NOT NULL);

-- ── Financial values ─────────────────────────────────────────
-- Just enough hand-curated history so each populated hero's own detail
-- page has a chart. Months computed relative to CURDATE(), oldest first.
-- (Cyberdue, Manifattura, Nexora, Ferramenta deliberately have none —
-- not every contract needs history for a clean demo.)

SET @ricavi := (SELECT id FROM financial_types WHERE name = 'Ricavi' AND organization_id = 3);
SET @area_it := (SELECT id FROM business_areas WHERE name = 'Servizi IT' AND organization_id = 3);
SET @area_finanza := (SELECT id FROM business_areas WHERE name = 'Finanza' AND organization_id = 3);
SET @area_legale := (SELECT id FROM business_areas WHERE name = 'Legale' AND organization_id = 3);
SET @area_operazioni := (SELECT id FROM business_areas WHERE name = 'Operazioni' AND organization_id = 3);
SET @solaris_contract := (SELECT id FROM contracts WHERE contract_number = 'NRT-2024-001' AND organization_id = 3);
SET @fintera_contract := (SELECT id FROM contracts WHERE contract_number = 'NRT-2024-002' AND organization_id = 3);
SET @trasporti_contract := (SELECT id FROM contracts WHERE contract_number = 'NRT-2024-005' AND organization_id = 3);
SET @vertice_contract := (SELECT id FROM contracts WHERE contract_number = 'NRT-2024-004' AND organization_id = 3);
SET @vertice_start := (SELECT start_date FROM contracts WHERE contract_number = 'NRT-2024-004' AND organization_id = 3);

-- Vertice Legal · Audit di Conformità — 3 quarterly rows generated from
-- the contract's own financial terms (see comment on the INSERT above).
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id, organization_id, source) VALUES
(MONTH(@vertice_start), YEAR(@vertice_start), 15000, @ricavi, @area_legale, @vertice_contract, 3, 'GENERATED'),
(MONTH(DATE_ADD(@vertice_start, INTERVAL 3 MONTH)), YEAR(DATE_ADD(@vertice_start, INTERVAL 3 MONTH)), 15000, @ricavi, @area_legale, @vertice_contract, 3, 'GENERATED'),
(MONTH(DATE_ADD(@vertice_start, INTERVAL 6 MONTH)), YEAR(DATE_ADD(@vertice_start, INTERVAL 6 MONTH)), 15000, @ricavi, @area_legale, @vertice_contract, 3, 'GENERATED');

-- Solaris Informatica · Migrazione Cloud — 18 months, steady growth.
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id, organization_id) VALUES
(MONTH(DATE_SUB(CURDATE(), INTERVAL 17 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 17 MONTH)), 84000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 16 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 16 MONTH)), 87000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 15 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 15 MONTH)), 86000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 14 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 14 MONTH)), 90000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 13 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 13 MONTH)), 93000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 12 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 12 MONTH)), 91000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 11 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 11 MONTH)), 96000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 10 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 10 MONTH)), 99000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)),  97000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)),  102000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)),  106000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)),  104000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)),  110000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)),  114000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)),  111000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 2 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 2 MONTH)),  118000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)),  123000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(CURDATE()), YEAR(CURDATE()), 129000, @ricavi, @area_it, @solaris_contract, 3);

-- Fintera Consulting · Reporting Finanziario — EXPIRED, 12 months ending
-- at its own end_date (4 months ago), none after — matches its status.
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id, organization_id) VALUES
(MONTH(DATE_SUB(CURDATE(), INTERVAL 15 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 15 MONTH)), 30000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 14 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 14 MONTH)), 32000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 13 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 13 MONTH)), 35000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 12 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 12 MONTH)), 33000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 11 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 11 MONTH)), 36000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 10 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 10 MONTH)), 38000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)),  40000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)),  38000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)),  42000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)),  45000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)),  48000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)),  46000, @ricavi, @area_finanza, @fintera_contract, 3);

-- Trasporti Meridiana · Supply Chain — active, 10 months of modest,
-- steady history (keeps the Operazioni area represented in the dashboard
-- totals too, not just IT/Finanza).
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id, organization_id) VALUES
(MONTH(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)), 22000, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)), 23500, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)), 21000, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)), 24500, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)), 25000, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)), 23800, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)), 26200, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 2 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 2 MONTH)), 27000, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)), 25800, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(CURDATE()), YEAR(CURDATE()), 28000, @ricavi, @area_operazioni, @trasporti_contract, 3);

-- ── Budgets ──────────────────────────────────────────────────
-- One REVENUE + COST target per area for the current year, so the Budget
-- vs Actual dashboard isn't empty for a visitor exploring it.
INSERT INTO budgets (business_area_id, category, year_value, target_amount, organization_id)
SELECT id, 'REVENUE', YEAR(CURDATE()), CASE name
    WHEN 'Servizi IT' THEN 800000 WHEN 'Operazioni' THEN 400000
    WHEN 'Legale' THEN 250000 WHEN 'Finanza' THEN 300000 END,
    3
FROM business_areas WHERE organization_id = 3
ON DUPLICATE KEY UPDATE target_amount = VALUES(target_amount);

INSERT INTO budgets (business_area_id, category, year_value, target_amount, organization_id)
SELECT id, 'COST', YEAR(CURDATE()), CASE name
    WHEN 'Servizi IT' THEN 200000 WHEN 'Operazioni' THEN 150000
    WHEN 'Legale' THEN 80000 WHEN 'Finanza' THEN 100000 END,
    3
FROM business_areas WHERE organization_id = 3
ON DUPLICATE KEY UPDATE target_amount = VALUES(target_amount);

SELECT 'Demo org reseeded' AS result;
SELECT COUNT(*) AS total_contracts FROM contracts WHERE organization_id = 3;
SELECT COUNT(*) AS total_financial_values FROM financial_values WHERE organization_id = 3;
SELECT COUNT(*) AS total_counterparties FROM counterparties WHERE organization_id = 3;
