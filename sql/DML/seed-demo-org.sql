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
-- PRO, not FREE: since tier gating (TierLimitService.requireMlAccess) FREE
-- orgs get 403 on every ML feature, which would hide the forecast/AI
-- widgets the public demo exists to show. Re-applied every night so a
-- stray manual change can't leave the demo locked.
UPDATE organizations SET name = 'Nortiva Solutions S.r.l.', slug = 'nortiva-solutions',
    subscription_tier = 'PRO' WHERE id = 3;

-- Nortiva's own (synthetic, checksum-valid) bank account: the debtor of every
-- SEPA transfer, which can't be generated without it. Re-applied nightly.
UPDATE organizations SET iban = 'IT05X0760101600000045821936', bic = 'NRTVITM1XXX' WHERE id = 3;

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
-- Notifications point at contracts by number in their text, not by FK, so
-- they outlive the contracts above: without this the bell kept showing
-- alerts about contracts long gone (e.g. "SYN-03-0028", a Faker leftover).
DELETE FROM notifications WHERE org_id = 3;

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
SELECT 'Cyberdue Security S.r.l.', 'SUPPLIER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Cyberdue Security S.r.l.' AND organization_id = 3);
-- A supplier: Nortiva bought the security upgrade from it. Set on every run,
-- since the row predates this and was created as a customer.
UPDATE counterparties SET type = 'SUPPLIER' WHERE name = 'Cyberdue Security S.r.l.' AND organization_id = 3;

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Manifattura Orion S.p.A.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Manifattura Orion S.p.A.' AND organization_id = 3);

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Nexora Technologies S.r.l.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Nexora Technologies S.r.l.' AND organization_id = 3);

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Ferramenta Bassano S.r.l.', 'CUSTOMER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Ferramenta Bassano S.r.l.' AND organization_id = 3);

-- Two SUPPLIERS: Nortiva buys from them, receives their FatturaPA invoices
-- and pays them by SEPA transfer. Every counterparty above is a customer, so
-- before these existed the demo had no purchase side at all: zero costs, and
-- incoming invoices had to be attached to customer contracts, where they could
-- only ever show "Fornitore non corrisponde". VAT numbers and IBANs are
-- invented but checksum-valid, and are shared with
-- scripts/seed_demo_purchase_invoices.py (the invoices must carry the same
-- VAT to match, and the same IBAN to pass the SEPA anti-fraud check).
-- verified_iban is the account BCM already trusts for that supplier; it is
-- re-applied every night so a visitor's actions can't change the story.
INSERT INTO counterparties (name, type, organization_id)
SELECT 'Nuvola Cloud Services S.r.l.', 'SUPPLIER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Nuvola Cloud Services S.r.l.' AND organization_id = 3);
UPDATE counterparties SET type = 'SUPPLIER', vat_number = 'IT07824150960',
    verified_iban = 'IT70K0306909400000071528400', verified_bic = 'NUVLITM1XXX',
    contact_email = 'amministrazione@nuvolacloud.example'
WHERE name = 'Nuvola Cloud Services S.r.l.' AND organization_id = 3;

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Ferri & Associati S.r.l.', 'SUPPLIER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Ferri & Associati S.r.l.' AND organization_id = 3);
UPDATE counterparties SET type = 'SUPPLIER', vat_number = 'IT05391280772',
    verified_iban = 'IT17M0503411701000062194812', verified_bic = 'FERRITM1XXX',
    contact_email = 'studio@ferriassociati.example'
WHERE name = 'Ferri & Associati S.r.l.' AND organization_id = 3;

-- Third SUPPLIER: plant maintenance with a tacit renewal, the most common
-- way an SME ends up paying a year it didn't want. No VAT or IBAN: it has no
-- invoices in the demo, its story is the notice deadline.
INSERT INTO counterparties (name, type, organization_id)
SELECT 'Rossi Impianti S.r.l.', 'SUPPLIER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Rossi Impianti S.r.l.' AND organization_id = 3);
UPDATE counterparties SET type = 'SUPPLIER', contact_email = 'commerciale@rossiimpianti.example'
WHERE name = 'Rossi Impianti S.r.l.' AND organization_id = 3;

-- Two more tacitly renewing suppliers, the contracts an SME most often
-- forgets: a software licence (notice due within the week) and office
-- cleaning (notice due in a month and a half, not urgent yet).
INSERT INTO counterparties (name, type, organization_id)
SELECT 'Gestio Software S.r.l.', 'SUPPLIER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Gestio Software S.r.l.' AND organization_id = 3);
UPDATE counterparties SET type = 'SUPPLIER', contact_email = 'rinnovi@gestiosoftware.example'
WHERE name = 'Gestio Software S.r.l.' AND organization_id = 3;

INSERT INTO counterparties (name, type, organization_id)
SELECT 'Aurora Servizi S.r.l.', 'SUPPLIER', 3
WHERE NOT EXISTS (SELECT 1 FROM counterparties WHERE name = 'Aurora Servizi S.r.l.' AND organization_id = 3);
UPDATE counterparties SET type = 'SUPPLIER', contact_email = 'clienti@auroraservizi.example'
WHERE name = 'Aurora Servizi S.r.l.' AND organization_id = 3;

-- A contact person for every counterparty (invented names, .example email
-- domains), so the Controparti list isn't a column of "N/D". No VAT numbers
-- for the customers on purpose: an invented one could belong to a real company.
UPDATE counterparties SET contact_name = 'Giulia Ferraro', contact_email = 'g.ferraro@solaris.example' WHERE name = 'Solaris Informatica S.p.A.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Andrea Conti', contact_email = 'a.conti@fintera.example' WHERE name = 'Fintera Consulting S.r.l.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Elena Marino', contact_email = 'e.marino@verticelegal.example' WHERE name = 'Vertice Legal S.r.l.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Paolo Greco', contact_email = 'p.greco@meridiana.example' WHERE name = 'Trasporti Meridiana S.r.l.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Sara Lombardi', contact_email = 's.lombardi@cyberdue.example' WHERE name = 'Cyberdue Security S.r.l.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Luca Bianchi', contact_email = 'l.bianchi@orion.example' WHERE name = 'Manifattura Orion S.p.A.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Chiara Romano', contact_email = 'c.romano@nexora.example' WHERE name = 'Nexora Technologies S.r.l.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Marco Gallo', contact_email = 'm.gallo@ferramentabassano.example' WHERE name = 'Ferramenta Bassano S.r.l.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Davide Costa' WHERE name = 'Nuvola Cloud Services S.r.l.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Roberta Ferri' WHERE name = 'Ferri & Associati S.r.l.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Giorgio Rossi' WHERE name = 'Rossi Impianti S.r.l.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Valentina Neri' WHERE name = 'Gestio Software S.r.l.' AND organization_id = 3;
UPDATE counterparties SET contact_name = 'Francesca Moretti' WHERE name = 'Aurora Servizi S.r.l.' AND organization_id = 3;

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
       'NRT-104', 'WBS-LEG-001', 'Audit di Conformità',
       (SELECT id FROM business_areas WHERE name='Legale' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 8 MONTH), DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'ACTIVE', 3,
       (SELECT id FROM financial_types WHERE name='Ricavi' AND organization_id=3), 60000, 'QUARTERLY';

-- 2) Expired, with 12 months of financial history ending at its own
-- end_date (4 months ago), none after — matches its status.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Fintera Consulting S.r.l.' AND organization_id=3),
       'NRT-102', 'WBS-FIN-001', 'Automazione Reporting Finanziario',
       (SELECT id FROM business_areas WHERE name='Finanza' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 16 MONTH), DATE_SUB(CURDATE(), INTERVAL 4 MONTH), 'EXPIRED', 3;

-- 3) Cancelled.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Cyberdue Security S.r.l.' AND organization_id=3),
       'NRT-106', 'WBS-IT-002', 'Aggiornamento Sicurezza Informatica',
       (SELECT id FROM business_areas WHERE name='Servizi IT' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 16 MONTH), DATE_SUB(CURDATE(), INTERVAL 10 MONTH), 'CANCELLED', 3;

-- 4) Second near-expiry (25 days), so the "scadenze imminenti" widget has
-- more than one row during the demo.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Manifattura Orion S.p.A.' AND organization_id=3),
       'NRT-112', 'WBS-OPS-002', 'Trasformazione Digitale',
       (SELECT id FROM business_areas WHERE name='Operazioni' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 9 MONTH), DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'ACTIVE', 3;

-- 5) Long-running IT contract (about 10-13k a month), 18 months of steady-growth financial
-- history for a chart with real shape on its detail page.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Solaris Informatica S.p.A.' AND organization_id=3),
       'NRT-101', 'WBS-IT-001', 'Migrazione Cloud Fase 1',
       (SELECT id FROM business_areas WHERE name='Servizi IT' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 30 MONTH), DATE_ADD(CURDATE(), INTERVAL 5 MONTH), 'ACTIVE', 3;

-- 6) Active operations contract, 10 months of steady financial history.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Trasporti Meridiana S.r.l.' AND organization_id=3),
       'NRT-105', 'WBS-OPS-001', 'Ottimizzazione Supply Chain',
       (SELECT id FROM business_areas WHERE name='Operazioni' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 10 MONTH), DATE_ADD(CURDATE(), INTERVAL 60 DAY), 'ACTIVE', 3;

-- 7) Recently started, long horizon.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Nexora Technologies S.r.l.' AND organization_id=3),
       'NRT-121', 'WBS-IT-003', 'Sviluppo Piattaforma AI',
       (SELECT id FROM business_areas WHERE name='Servizi IT' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 1 MONTH), DATE_ADD(CURDATE(), INTERVAL 24 MONTH), 'ACTIVE', 3;

-- 8) Workflow demo: DRAFT, sitting in IN_REVIEW, submitted by the "demo"
-- user. No public login can approve it (the public demo is deliberately
-- MANAGER-only — see header), so this shows the workflow UI/history
-- exists without exposing an admin/approver account publicly.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, workflow_stage, organization_id)
SELECT (SELECT id FROM counterparties WHERE name='Ferramenta Bassano S.r.l.' AND organization_id=3),
       'NRT-122', 'WBS-FIN-002', 'Fornitura Software Gestionale',
       (SELECT id FROM business_areas WHERE name='Finanza' AND organization_id=3),
       4, DATE_ADD(CURDATE(), INTERVAL 15 DAY), NULL, 'DRAFT', 'IN_REVIEW', 3;

-- 9) Purchase: monthly cloud hosting, the invoice -> match -> SEPA demo.
-- Its invoices are attached nightly by seed_demo_purchase_invoices.py: last
-- month's is matched and already paid, this month's is waiting to be matched.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id, financial_type_id, annual_value, billing_frequency)
SELECT (SELECT id FROM counterparties WHERE name='Nuvola Cloud Services S.r.l.' AND organization_id=3),
       'NRT-117', 'WBS-IT-004', 'Hosting e servizi cloud',
       (SELECT id FROM business_areas WHERE name='Servizi IT' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 12 MONTH), DATE_ADD(CURDATE(), INTERVAL 12 MONTH), 'ACTIVE', 3,
       (SELECT id FROM financial_types WHERE name='Costi' AND organization_id=3), 57600, 'MONTHLY';

-- 10) Purchase: quarterly tax advisory. Its latest invoice asks to be paid
-- on a different IBAN than the trusted one, so the SEPA anti-fraud check
-- blocks the transfer — the classic "supplier changed bank details" scam.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id, financial_type_id, annual_value, billing_frequency)
SELECT (SELECT id FROM counterparties WHERE name='Ferri & Associati S.r.l.' AND organization_id=3),
       'NRT-118', 'WBS-FIN-003', 'Consulenza fiscale e societaria',
       (SELECT id FROM business_areas WHERE name='Finanza' AND organization_id=3),
       4, DATE_SUB(CURDATE(), INTERVAL 10 MONTH), DATE_ADD(CURDATE(), INTERVAL 14 MONTH), 'ACTIVE', 3,
       (SELECT id FROM financial_types WHERE name='Costi' AND organization_id=3), 26000, 'QUARTERLY';

-- 11) Purchase with a tacit renewal: 6,000/year plant maintenance, renews
-- for 12 months unless cancelled 90 days before the end. The end date is 110
-- days away, so the last day to give notice is in 20 days: the home page
-- lists it, and the 30/14/7/1-day reminders target that day, not the end.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id, auto_renew, notice_days, renewal_months)
SELECT (SELECT id FROM counterparties WHERE name='Rossi Impianti S.r.l.' AND organization_id=3),
       'NRT-119', 'WBS-OPS-003', 'Manutenzione impianti',
       (SELECT id FROM business_areas WHERE name='Operazioni' AND organization_id=3),
       4, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 110 DAY), INTERVAL 12 MONTH), DATE_ADD(CURDATE(), INTERVAL 110 DAY),
       'ACTIVE', 3, TRUE, 90, 12;

-- 12) Software licence, renews yearly unless cancelled 30 days before the
-- end; the end is 36 days away, so the last day to give notice is in 6 days:
-- the most urgent item on the home page.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id, auto_renew, notice_days, renewal_months)
SELECT (SELECT id FROM counterparties WHERE name='Gestio Software S.r.l.' AND organization_id=3),
       'NRT-123', NULL, 'Licenza software gestionale',
       (SELECT id FROM business_areas WHERE name='Servizi IT' AND organization_id=3),
       4, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 36 DAY), INTERVAL 12 MONTH), DATE_ADD(CURDATE(), INTERVAL 36 DAY),
       'ACTIVE', 3, TRUE, 30, 12;

-- 13) Office cleaning, a two-year contract renewing for 12 months unless
-- cancelled 60 days before the end; notice due in 45 days, outside the home
-- page's 30-day window: listed with its deadline, not yet urgent. No WBS
-- code, like most real contracts: the field is optional.
INSERT INTO contracts (counterparty_id, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status, organization_id, auto_renew, notice_days, renewal_months)
SELECT (SELECT id FROM counterparties WHERE name='Aurora Servizi S.r.l.' AND organization_id=3),
       'NRT-124', NULL, 'Pulizia uffici',
       (SELECT id FROM business_areas WHERE name='Operazioni' AND organization_id=3),
       4, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 105 DAY), INTERVAL 24 MONTH), DATE_ADD(CURDATE(), INTERVAL 105 DAY),
       'ACTIVE', 3, TRUE, 60, 12;

-- Yearly value of the three tacitly renewing purchases, so each supplier's
-- page shows what the contract is worth instead of "0 €". No monthly cost
-- rows for them: their story is the notice deadline, not the invoices.
UPDATE contracts SET financial_type_id = (SELECT id FROM financial_types WHERE name = 'Costi' AND organization_id = 3),
       annual_value = 6000, billing_frequency = 'QUARTERLY'
WHERE contract_number = 'NRT-119' AND organization_id = 3;
UPDATE contracts SET financial_type_id = (SELECT id FROM financial_types WHERE name = 'Costi' AND organization_id = 3),
       annual_value = 2400, billing_frequency = 'ANNUAL'
WHERE contract_number = 'NRT-123' AND organization_id = 3;
UPDATE contracts SET financial_type_id = (SELECT id FROM financial_types WHERE name = 'Costi' AND organization_id = 3),
       annual_value = 9600, billing_frequency = 'MONTHLY'
WHERE contract_number = 'NRT-124' AND organization_id = 3;

INSERT INTO contract_workflow_events (contract_id, from_stage, to_stage, action, actor_user_id, comment, created_at)
SELECT (SELECT id FROM contracts WHERE contract_number = 'NRT-122' AND organization_id = 3),
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
SET @costi := (SELECT id FROM financial_types WHERE name = 'Costi' AND organization_id = 3);
SET @area_it := (SELECT id FROM business_areas WHERE name = 'Servizi IT' AND organization_id = 3);
SET @area_finanza := (SELECT id FROM business_areas WHERE name = 'Finanza' AND organization_id = 3);
SET @area_legale := (SELECT id FROM business_areas WHERE name = 'Legale' AND organization_id = 3);
SET @area_operazioni := (SELECT id FROM business_areas WHERE name = 'Operazioni' AND organization_id = 3);
SET @solaris_contract := (SELECT id FROM contracts WHERE contract_number = 'NRT-101' AND organization_id = 3);
SET @fintera_contract := (SELECT id FROM contracts WHERE contract_number = 'NRT-102' AND organization_id = 3);
SET @trasporti_contract := (SELECT id FROM contracts WHERE contract_number = 'NRT-105' AND organization_id = 3);
SET @vertice_contract := (SELECT id FROM contracts WHERE contract_number = 'NRT-104' AND organization_id = 3);
SET @vertice_start := (SELECT start_date FROM contracts WHERE contract_number = 'NRT-104' AND organization_id = 3);
SET @nuvola_contract := (SELECT id FROM contracts WHERE contract_number = 'NRT-117' AND organization_id = 3);
SET @ferri_contract := (SELECT id FROM contracts WHERE contract_number = 'NRT-118' AND organization_id = 3);

-- Vertice Legal · Audit di Conformità — 3 quarterly rows generated from
-- the contract's own financial terms (see comment on the INSERT above).
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id, organization_id, source) VALUES
(MONTH(@vertice_start), YEAR(@vertice_start), 15000, @ricavi, @area_legale, @vertice_contract, 3, 'GENERATED'),
(MONTH(DATE_ADD(@vertice_start, INTERVAL 3 MONTH)), YEAR(DATE_ADD(@vertice_start, INTERVAL 3 MONTH)), 15000, @ricavi, @area_legale, @vertice_contract, 3, 'GENERATED'),
(MONTH(DATE_ADD(@vertice_start, INTERVAL 6 MONTH)), YEAR(DATE_ADD(@vertice_start, INTERVAL 6 MONTH)), 15000, @ricavi, @area_legale, @vertice_contract, 3, 'GENERATED');

-- Solaris Informatica · Migrazione Cloud — 18 months, steady growth.
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id, organization_id) VALUES
(MONTH(DATE_SUB(CURDATE(), INTERVAL 17 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 17 MONTH)), 8400, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 16 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 16 MONTH)), 8700, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 15 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 15 MONTH)), 8600, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 14 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 14 MONTH)), 9000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 13 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 13 MONTH)), 9300, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 12 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 12 MONTH)), 9100, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 11 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 11 MONTH)), 9600, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 10 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 10 MONTH)), 9900, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)),  9700, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)),  10200, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)),  10600, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)),  10400, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)),  11000, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)),  11400, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)),  11100, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 2 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 2 MONTH)),  11800, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)),  12300, @ricavi, @area_it, @solaris_contract, 3),
(MONTH(CURDATE()), YEAR(CURDATE()), 12900, @ricavi, @area_it, @solaris_contract, 3);

-- Fintera Consulting · Reporting Finanziario — EXPIRED, 12 months ending
-- at its own end_date (4 months ago), none after — matches its status.
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id, organization_id) VALUES
(MONTH(DATE_SUB(CURDATE(), INTERVAL 15 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 15 MONTH)), 3000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 14 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 14 MONTH)), 3200, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 13 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 13 MONTH)), 3500, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 12 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 12 MONTH)), 3300, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 11 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 11 MONTH)), 3600, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 10 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 10 MONTH)), 3800, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)),  4000, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)),  3800, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)),  4200, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)),  4500, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)),  4800, @ricavi, @area_finanza, @fintera_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)),  4600, @ricavi, @area_finanza, @fintera_contract, 3);

-- Trasporti Meridiana · Supply Chain — active, 10 months of modest,
-- steady history (keeps the Operazioni area represented in the dashboard
-- totals too, not just IT/Finanza).
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id, organization_id) VALUES
(MONTH(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)), 2200, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)), 2350, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)), 2100, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)), 2450, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)), 2500, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)), 2380, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)), 2620, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 2 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 2 MONTH)), 2700, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)), 2580, @ricavi, @area_operazioni, @trasporti_contract, 3),
(MONTH(CURDATE()), YEAR(CURDATE()), 2800, @ricavi, @area_operazioni, @trasporti_contract, 3);

-- Nuvola Cloud · Hosting — 12 monthly costs of 4,800 (taxable amount, VAT excluded:
-- BCM compares contract amounts with an invoice's taxable amount). The
-- purchase invoices match these by amount and month.
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id, organization_id) VALUES
(MONTH(DATE_SUB(CURDATE(), INTERVAL 11 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 11 MONTH)), 4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 10 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 10 MONTH)), 4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)),  4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 8 MONTH)),  4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 7 MONTH)),  4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)),  4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 5 MONTH)),  4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 4 MONTH)),  4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)),  4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 2 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 2 MONTH)),  4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)),  YEAR(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)),  4800, @costi, @area_it, @nuvola_contract, 3),
(MONTH(CURDATE()), YEAR(CURDATE()), 4800, @costi, @area_it, @nuvola_contract, 3);

-- Ferri & Associati · Consulenza — quarterly costs of 6,500 (taxable amount, VAT excluded).
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id, organization_id) VALUES
(MONTH(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 9 MONTH)), 6500, @costi, @area_finanza, @ferri_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 6 MONTH)), 6500, @costi, @area_finanza, @ferri_contract, 3),
(MONTH(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)), YEAR(DATE_SUB(CURDATE(), INTERVAL 3 MONTH)), 6500, @costi, @area_finanza, @ferri_contract, 3),
(MONTH(CURDATE()), YEAR(CURDATE()), 6500, @costi, @area_finanza, @ferri_contract, 3);

-- ── Budgets ──────────────────────────────────────────────────
-- One target per area and category for the current year, derived from the
-- year-to-date values above instead of fixed amounts: the financial values
-- move with CURDATE(), so fixed targets drifted into an all-red page.
--
--   target = year-to-date actual / (months elapsed / 12) * factor
--
-- which makes "actual / target" equal "share of the year elapsed / factor"
-- whatever night the reset runs, so every area keeps telling the same story
-- against the page's "expected by today" marker:
--   revenue  Servizi IT 0.92 ahead · Finanza 1.00 on pace ·
--            Operazioni 1.12 slightly behind · Legale 1.50 well behind
--   cost     Servizi IT 1.20 · Finanza 1.25, both under control
-- Areas with nothing booked this year get no budget (early January, or no
-- costs at all for Operazioni/Legale) rather than a target with no data.
SET @months_elapsed_share := MONTH(CURDATE()) / 12;

INSERT INTO budgets (business_area_id, category, year_value, target_amount, organization_id)
SELECT ytd.area_id, ytd.category, YEAR(CURDATE()),
       ROUND(ytd.total / @months_elapsed_share * CASE CONCAT(ytd.category, ':', ba.name)
           WHEN 'REVENUE:Servizi IT' THEN 0.92 WHEN 'REVENUE:Finanza' THEN 1.00
           WHEN 'REVENUE:Operazioni' THEN 1.12 WHEN 'REVENUE:Legale' THEN 1.50
           WHEN 'COST:Servizi IT' THEN 1.20 WHEN 'COST:Finanza' THEN 1.25
           ELSE 1.00 END, -3),
       3
FROM (
    SELECT fv.area_id, ft.category, SUM(fv.financial_amount) AS total
    FROM financial_values fv
    JOIN financial_types ft ON ft.id = fv.financial_type_id
    WHERE fv.organization_id = 3 AND fv.year_value = YEAR(CURDATE())
    GROUP BY fv.area_id, ft.category
) ytd
JOIN business_areas ba ON ba.id = ytd.area_id AND ba.organization_id = 3
WHERE ytd.total > 0;

SELECT 'Demo org reseeded' AS result;
SELECT COUNT(*) AS total_contracts FROM contracts WHERE organization_id = 3;
SELECT COUNT(*) AS total_financial_values FROM financial_values WHERE organization_id = 3;
SELECT COUNT(*) AS total_counterparties FROM counterparties WHERE organization_id = 3;
