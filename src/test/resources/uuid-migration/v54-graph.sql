-- A V54 data set touching every table and every kind of reference V55 converts
-- (UuidMigrationIT). Explicit numeric ids, far above anything the migrations seed.

SET @admin_role = (SELECT id FROM roles WHERE role = 'ADMIN');
SET @manager_role = (SELECT id FROM roles WHERE role = 'MANAGER');

INSERT INTO organizations (id, name, slug, subscription_tier) VALUES
    (100001, 'Alfa S.r.l.', 'alfa', 'PRO'), (100002, 'Beta S.p.A.', 'beta', 'FREE');

INSERT INTO managers (id, first_name, last_name, email, organization_id) VALUES
    (110001, 'Anna', 'Alfa', 'anna@alfa.test', 100001), (110002, 'Bruno', 'Alfa', 'bruno@alfa.test', 100001),
    (110003, 'Carla', 'Beta', 'carla@beta.test', 100002);

INSERT INTO users (id, username, password_hash, manager_id, role_id, verified, organization_id) VALUES
    (120001, 'anna.alfa', 'h', 110001, @admin_role, 1, 100001),
    (120002, 'bruno.alfa', 'h', 110002, @manager_role, 1, 100001),
    (120003, 'carla.beta', 'h', 110003, @admin_role, 1, 100002);

INSERT INTO business_areas (id, name, organization_id) VALUES
    (130001, 'Operazioni', 100001), (130002, 'Finanza', 100001), (130003, 'Operazioni', 100002);
INSERT INTO counterparties (id, name, type, organization_id) VALUES
    (140001, 'Cliente Uno', 'CUSTOMER', 100001), (140002, 'Fornitore Due', 'SUPPLIER', 100001),
    (140003, 'Cliente Uno', 'CUSTOMER', 100002);
INSERT INTO financial_types (id, name, organization_id, category) VALUES
    (150001, 'Canone', 100001, 'REVENUE'), (150002, 'Canone', 100002, 'REVENUE');

INSERT INTO contracts (id, contract_number, start_date, end_date, status, organization_id, counterparty_id, area_id, manager_id, financial_type_id) VALUES
    (160001, 'ALF-1', '2026-01-01', '2026-12-31', 'ACTIVE', 100001, 140001, 130001, 110001, 150001),
    (160002, 'ALF-2', '2026-02-01', NULL, 'DRAFT', 100001, 140002, 130002, 110002, NULL),
    (160003, 'ALF-1', '2026-03-01', '2027-02-28', 'ACTIVE', 100002, 140003, 130003, 110003, 150002);
INSERT INTO contract_manager (contract_id, manager_id) VALUES (160001, 110002), (160002, 110001);

INSERT INTO financial_values (id, month_value, year_value, financial_amount, organization_id, contract_id, financial_type_id, area_id) VALUES
    (170001, 1, 2026, 1000, 100001, 160001, 150001, 130001),
    (170002, 2, 2026, 1100, 100001, 160001, 150001, 130001),
    (170003, 1, 2026, 500, 100001, NULL, NULL, NULL),
    (170004, 3, 2026, 900, 100002, 160003, 150002, 130003);
INSERT INTO budgets (id, business_area_id, category, year_value, target_amount, organization_id) VALUES
    (180001, 130001, 'REVENUE', 2026, 5000, 100001), (180002, 130003, 'REVENUE', 2026, 3000, 100002);
INSERT INTO contract_templates (id, name, org_id, business_area_id, default_manager_id) VALUES
    (190001, 'Standard', 100001, 130001, 110001), (190002, 'Vuoto', 100002, NULL, NULL);

INSERT INTO contract_history (id, contract_id, modified_by, modification_date, previous_status, new_status) VALUES
    (200001, 160001, 120001, '2026-01-02 10:00:00', 'DRAFT', 'ACTIVE'),
    (200002, 160002, NULL, '2026-02-02 10:00:00', NULL, 'DRAFT');
INSERT INTO contract_workflow_events (id, contract_id, to_stage, action, actor_user_id, created_at) VALUES
    (210001, 160001, 'APPROVED', 'APPROVE', 120001, '2026-01-03 09:00:00');

INSERT INTO contract_documents (id, contract_id, storage_path, file_name, file_size, org_id, version_group_id, version_number) VALUES
    (220001, 160001, 'documents/100001/160001/a.pdf', 'a.pdf', 10, 100001, 220001, 1),
    (220002, 160001, 'documents/100001/160001/b.pdf', 'a.pdf', 12, 100001, 220001, 2),
    (220003, 160003, 'documents/0/160003/c.pdf', 'c.pdf', 9, NULL, NULL, 1);
INSERT INTO sepa_payment_batches (id, contract_id, org_id, message_id, execution_date, total_amount, currency, number_of_transactions, storage_path, file_name) VALUES
    (230001, 160001, 100001, 'MSG-ALFA-1', '2026-02-10', 1000.00, 'EUR', 1, 'sepa/100001/160001/s.xml', 's.xml');
INSERT INTO electronic_invoices (id, contract_id, storage_path, file_name, file_size, org_id, matched_financial_value_id, matched_by_user_id, sepa_batch_id, invoice_number) VALUES
    (240001, 160001, 'invoices/100001/160001/i1.xml', 'i1.xml', 100, 100001, 170001, 120001, 230001, 'F-1'),
    (240002, 160003, 'invoices/100002/160003/i2.xml', 'i2.xml', 80, 100002, NULL, NULL, NULL, 'F-2');
INSERT INTO invoice_line_items (id, invoice_id, line_number, description, total_price) VALUES
    (250001, 240001, 1, 'Canone gennaio', 800), (250002, 240001, 2, 'Assistenza', 200);
INSERT INTO risk_feedback (id, contract_id, submitted_by, org_id, risk_score, risk_level, agree, created_at) VALUES
    (260001, 160001, 120002, 100001, 0.7, 'HIGH', 1, '2026-01-05 08:00:00');

INSERT INTO notifications (id, user_id, org_id, title, message, created_at) VALUES
    (270001, 120001, 100001, 'Scadenza', 'ALF-1 scade', '2026-01-06 08:00:00'),
    (270002, 120003, 100002, 'Scadenza', 'ALF-1 scade', '2026-01-06 08:00:00'),
    (270003, 120002, 199999, 'Vecchia', 'organizzazione cancellata', '2026-01-07 08:00:00'),
    (270004, 120002, 199999, 'Vecchia 2', 'stessa organizzazione cancellata', '2026-01-07 09:00:00');

INSERT INTO password_reset_token (id, token, expiry_date, user_id) VALUES (280001, 'reset-anna', '2030-01-01 00:00:00', 120001);
INSERT INTO verification_token (id, token, expiry_date, user_id) VALUES (280002, 'verify-bruno', '2030-01-01 00:00:00', 120002);
INSERT INTO invite_token (id, token, expiry_date, username, role, manager_id, used) VALUES
    (280003, 'invite-bruno', '2030-01-01 00:00:00', 'nuovo', 'MANAGER', 110002, 0);
INSERT INTO totp_recovery_codes (id, user_id, code_hash) VALUES (280004, 120001, 'c1'), (280005, 120001, 'c2');
INSERT INTO organization_deletion_codes (id, organization_id, user_id, code_hash, code_sent_at, expires_at) VALUES
    (280006, 100002, 120003, 'hash', '2026-01-08 08:00:00', '2026-01-08 08:15:00');
INSERT INTO refresh_tokens (id, token, user_id, expiry_date) VALUES (280007, 'refresh-anna', 120001, '2030-01-01 00:00:00');
INSERT INTO ml_result_cache (id, org_id, cache_key, json_result, computed_at) VALUES
    (280008, 100001, 'FORECAST_3_mgr_110001', '{}', '2026-01-09 08:00:00');
INSERT INTO pending_registrations (id, public_id, organization_name, admin_first_name, admin_last_name, admin_email, admin_username,
    password_hash, terms_version, code_hash, code_sent_at, expires_at) VALUES
    (280009, '00000000-0000-4000-8000-000000000001', 'Gamma', 'G', 'G', 'g@gamma.test', 'g', 'h', '2026-10-28', 'c',
     '2026-01-10 08:00:00', '2026-01-10 08:15:00');
INSERT INTO terms_acceptances (id, organization_id, organization_name, user_id, username, signer_email, terms_version,
    onerous_clauses, professional_use_declared, signature_method, code_sent_at, accepted_at) VALUES
    (290001, 100001, 'Alfa S.r.l.', 120001, 'anna.alfa', 'anna@alfa.test', '2026-10-28', 'all', 1, 'EMAIL_OTP',
     '2026-01-01 08:00:00', '2026-01-01 08:05:00'),
    (290002, 199999, 'Cancellata S.r.l.', 199998, 'ex.admin', 'ex@gone.test', '2026-10-28', 'all', 1, 'EMAIL_OTP',
     '2025-12-01 08:00:00', '2025-12-01 08:05:00');

-- Audit: types that name a table, types that do not, values with no row, an
-- organization that no longer exists, and entries with no organization at all.
INSERT INTO audit_logs (id, action, entity_type, entity_id, username, org_id, timestamp, details) VALUES
    (300001, 'CREATE', 'Contract', 160001, 'anna.alfa', 100001, '2026-01-01 10:00:00', 'ContractService.create#160001'),
    (300002, 'UPDATE', 'Contract', 160001, 'anna.alfa', 100001, '2026-01-01 11:00:00', 'ContractService.update#160001'),
    (300003, 'UPDATE', 'Organization', 100002, 'carla.beta', 100002, '2026-01-01 12:00:00', 'OrganizationService.update'),
    (300004, 'UPDATE', 'User', 120002, 'anna.alfa', 100001, '2026-01-01 13:00:00', 'UserService.update'),
    (300005, 'CREATE', 'Counterparty', 140001, 'anna.alfa', 100001, '2026-01-01 14:00:00', 'CounterpartyService.create'),
    (300006, 'CREATE', 'Manager', 110002, 'anna.alfa', 100001, '2026-01-01 15:00:00', 'ManagerService.create'),
    (300007, 'CREATE', 'BusinessArea', 130002, 'anna.alfa', 100001, '2026-01-01 16:00:00', 'BusinessAreaService.create'),
    (300008, 'CREATE', 'FinancialType', 150001, 'anna.alfa', 100001, '2026-01-01 17:00:00', 'FinancialTypeService.create'),
    (300009, 'DELETE', 'Contract', 169999, 'anna.alfa', 100001, '2026-01-02 10:00:00', 'ContractService.delete#169999'),
    (300010, 'CHECKOUT', 'Billing', 77, 'anna.alfa', 100001, '2026-01-02 11:00:00', 'BillingService.checkout'),
    (300011, 'CHECKOUT', 'Billing', 77, 'anna.alfa', 100001, '2026-01-02 12:00:00', 'BillingService.checkout again'),
    (300012, 'CREATE', 'RefreshToken', 280007, 'anna.alfa', 100001, '2026-01-02 13:00:00', 'RefreshTokenService.create'),
    (300013, 'DELETE', 'Organization', 199999, 'system:purge', 199999, '2026-01-03 10:00:00', 'OrganizationPurgeService.purge'),
    (300014, 'LOGIN', 'Auth', NULL, 'anna.alfa', NULL, '2026-01-03 11:00:00', 'AuthService.login');

-- Rows that violate a declared foreign key, as if checks had once been switched
-- off: a CASCADE one (V55 deletes it) and a SET NULL one (V55 clears it).
SET FOREIGN_KEY_CHECKS = 0;
INSERT INTO notifications (id, user_id, org_id, title, message, created_at) VALUES
    (270099, 129999, 100001, 'Orfana', 'utente cancellato', '2026-01-11 08:00:00');
UPDATE contract_templates SET default_manager_id = 119999 WHERE id = 190002;
SET FOREIGN_KEY_CHECKS = 1;
