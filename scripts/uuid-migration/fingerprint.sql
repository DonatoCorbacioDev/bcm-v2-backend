-- Fingerprint of every relationship, written without ids (ADR-0009).
--
-- Run it before and after V55: the two outputs must be identical. Relationships
-- are expressed through natural values (contract number, counterparty name,
-- username, slug...), so the same SQL works on BIGINT and UUID ids. References
-- to rows that no longer exist (audit entries outliving their organization) are
-- compared by how the rows are grouped: rows that pointed at the same missing
-- id must still point at the same one.
--
--   mysql bcm --batch --skip-column-names < scripts/uuid-migration/fingerprint.sql > before.txt

SET SESSION group_concat_max_len = 1000000;

SELECT 'count', 'audit_logs', COUNT(*) FROM audit_logs UNION ALL
SELECT 'count', 'budgets', COUNT(*) FROM budgets UNION ALL
SELECT 'count', 'business_areas', COUNT(*) FROM business_areas UNION ALL
SELECT 'count', 'contract_documents', COUNT(*) FROM contract_documents UNION ALL
SELECT 'count', 'contract_history', COUNT(*) FROM contract_history UNION ALL
SELECT 'count', 'contract_manager', COUNT(*) FROM contract_manager UNION ALL
SELECT 'count', 'contract_templates', COUNT(*) FROM contract_templates UNION ALL
SELECT 'count', 'contract_workflow_events', COUNT(*) FROM contract_workflow_events UNION ALL
SELECT 'count', 'contracts', COUNT(*) FROM contracts UNION ALL
SELECT 'count', 'counterparties', COUNT(*) FROM counterparties UNION ALL
SELECT 'count', 'electronic_invoices', COUNT(*) FROM electronic_invoices UNION ALL
SELECT 'count', 'financial_types', COUNT(*) FROM financial_types UNION ALL
SELECT 'count', 'financial_values', COUNT(*) FROM financial_values UNION ALL
SELECT 'count', 'invite_token', COUNT(*) FROM invite_token UNION ALL
SELECT 'count', 'invoice_line_items', COUNT(*) FROM invoice_line_items UNION ALL
SELECT 'count', 'managers', COUNT(*) FROM managers UNION ALL
SELECT 'count', 'notifications', COUNT(*) FROM notifications UNION ALL
SELECT 'count', 'organization_deletion_codes', COUNT(*) FROM organization_deletion_codes UNION ALL
SELECT 'count', 'organizations', COUNT(*) FROM organizations UNION ALL
SELECT 'count', 'password_reset_token', COUNT(*) FROM password_reset_token UNION ALL
SELECT 'count', 'pending_registrations', COUNT(*) FROM pending_registrations UNION ALL
SELECT 'count', 'risk_feedback', COUNT(*) FROM risk_feedback UNION ALL
SELECT 'count', 'roles', COUNT(*) FROM roles UNION ALL
SELECT 'count', 'sepa_payment_batches', COUNT(*) FROM sepa_payment_batches UNION ALL
SELECT 'count', 'terms_acceptances', COUNT(*) FROM terms_acceptances UNION ALL
SELECT 'count', 'totp_recovery_codes', COUNT(*) FROM totp_recovery_codes UNION ALL
SELECT 'count', 'users', COUNT(*) FROM users UNION ALL
SELECT 'count', 'verification_token', COUNT(*) FROM verification_token;

-- Organization-owned rows.
SELECT 'business_area', o.slug, b.name FROM business_areas b JOIN organizations o ON o.id = b.organization_id ORDER BY 2, 3;
SELECT 'counterparty', o.slug, c.name FROM counterparties c JOIN organizations o ON o.id = c.organization_id ORDER BY 2, 3;
SELECT 'financial_type', o.slug, t.name FROM financial_types t JOIN organizations o ON o.id = t.organization_id ORDER BY 2, 3;
SELECT 'manager', o.slug, m.email FROM managers m JOIN organizations o ON o.id = m.organization_id ORDER BY 2, 3;
SELECT 'user', u.username, o.slug, m.email, r.role FROM users u JOIN organizations o ON o.id = u.organization_id
    LEFT JOIN managers m ON m.id = u.manager_id JOIN roles r ON r.id = u.role_id ORDER BY 2;
SELECT 'contract', c.contract_number, o.slug, cp.name, b.name, m.email, t.name
    FROM contracts c JOIN organizations o ON o.id = c.organization_id JOIN counterparties cp ON cp.id = c.counterparty_id
    LEFT JOIN business_areas b ON b.id = c.area_id LEFT JOIN managers m ON m.id = c.manager_id
    LEFT JOIN financial_types t ON t.id = c.financial_type_id ORDER BY 3, 2;
SELECT 'collaborator', o.slug, c.contract_number, m.email FROM contract_manager cm JOIN contracts c ON c.id = cm.contract_id
    JOIN organizations o ON o.id = c.organization_id JOIN managers m ON m.id = cm.manager_id ORDER BY 2, 3, 4;
SELECT 'financial_value', o.slug, c.contract_number, t.name, b.name, v.year_value, v.month_value, v.financial_amount
    FROM financial_values v JOIN organizations o ON o.id = v.organization_id LEFT JOIN contracts c ON c.id = v.contract_id
    LEFT JOIN financial_types t ON t.id = v.financial_type_id LEFT JOIN business_areas b ON b.id = v.area_id
    ORDER BY 2, 3, 4, 6, 7, 8;
SELECT 'budget', o.slug, b.name, g.category, g.year_value, g.target_amount FROM budgets g
    JOIN organizations o ON o.id = g.organization_id JOIN business_areas b ON b.id = g.business_area_id ORDER BY 2, 3, 4, 5;
SELECT 'template', o.slug, t.name, b.name, m.email FROM contract_templates t JOIN organizations o ON o.id = t.org_id
    LEFT JOIN business_areas b ON b.id = t.business_area_id LEFT JOIN managers m ON m.id = t.default_manager_id ORDER BY 2, 3;

-- Contract children.
SELECT 'history', c.contract_number, h.modification_date, h.previous_status, h.new_status, u.username
    FROM contract_history h JOIN contracts c ON c.id = h.contract_id LEFT JOIN users u ON u.id = h.modified_by ORDER BY 2, 3, 4, 5;
SELECT 'workflow', c.contract_number, e.created_at, e.action, e.from_stage, e.to_stage, u.username
    FROM contract_workflow_events e JOIN contracts c ON c.id = e.contract_id JOIN users u ON u.id = e.actor_user_id ORDER BY 2, 3, 4;
SELECT 'document', d.storage_path, c.contract_number, o.slug, g.storage_path, d.version_number
    FROM contract_documents d JOIN contracts c ON c.id = d.contract_id LEFT JOIN organizations o ON o.id = d.org_id
    LEFT JOIN contract_documents g ON g.id = d.version_group_id ORDER BY 2;
SELECT 'sepa_batch', s.message_id, c.contract_number, o.slug FROM sepa_payment_batches s JOIN contracts c ON c.id = s.contract_id
    LEFT JOIN organizations o ON o.id = s.org_id ORDER BY 2;
SELECT 'invoice', i.storage_path, c.contract_number, o.slug, u.username, fc.contract_number, fv.year_value, fv.month_value, s.message_id
    FROM electronic_invoices i JOIN contracts c ON c.id = i.contract_id LEFT JOIN organizations o ON o.id = i.org_id
    LEFT JOIN users u ON u.id = i.matched_by_user_id LEFT JOIN financial_values fv ON fv.id = i.matched_financial_value_id
    LEFT JOIN contracts fc ON fc.id = fv.contract_id LEFT JOIN sepa_payment_batches s ON s.id = i.sepa_batch_id ORDER BY 2;
SELECT 'invoice_line', i.storage_path, l.line_number, l.description, l.total_price FROM invoice_line_items l
    JOIN electronic_invoices i ON i.id = l.invoice_id ORDER BY 2, 3, 4;
SELECT 'risk_feedback', c.contract_number, u.username, o.slug, f.created_at, f.risk_score FROM risk_feedback f
    JOIN contracts c ON c.id = f.contract_id JOIN users u ON u.id = f.submitted_by JOIN organizations o ON o.id = f.org_id ORDER BY 2, 5;

-- Per-user rows.
SELECT 'notification', u.username, o.slug, n.created_at, n.title FROM notifications n JOIN users u ON u.id = n.user_id
    LEFT JOIN organizations o ON o.id = n.org_id ORDER BY 2, 4, 5;
SELECT 'password_reset', t.token, u.username FROM password_reset_token t JOIN users u ON u.id = t.user_id ORDER BY 2;
SELECT 'verification', t.token, u.username FROM verification_token t JOIN users u ON u.id = t.user_id ORDER BY 2;
SELECT 'totp_codes', u.username, COUNT(*) FROM totp_recovery_codes t JOIN users u ON u.id = t.user_id GROUP BY u.username ORDER BY 2;
SELECT 'invite', t.token, m.email FROM invite_token t JOIN managers m ON m.id = t.manager_id ORDER BY 2;
SELECT 'deletion_code', o.slug, u.username, d.code_sent_at FROM organization_deletion_codes d
    JOIN organizations o ON o.id = d.organization_id JOIN users u ON u.id = d.user_id ORDER BY 2, 4;
SELECT 'terms', a.accepted_at, a.username, o.slug, u.username FROM terms_acceptances a
    LEFT JOIN organizations o ON o.id = a.organization_id LEFT JOIN users u ON u.id = a.user_id ORDER BY 2, 3;

-- Audit entries: organization, and the entity for the types that name a table.
SELECT 'audit', a.timestamp, a.action, a.entity_type, a.username, a.details, o.slug,
       COALESCE(c.contract_number, ox.slug, ux.username, cp.name, mx.email, bx.name, tx.name)
    FROM audit_logs a LEFT JOIN organizations o ON o.id = a.org_id
    LEFT JOIN contracts c ON a.entity_type = 'Contract' AND c.id = a.entity_id
    LEFT JOIN organizations ox ON a.entity_type = 'Organization' AND ox.id = a.entity_id
    LEFT JOIN users ux ON a.entity_type = 'User' AND ux.id = a.entity_id
    LEFT JOIN counterparties cp ON a.entity_type = 'Counterparty' AND cp.id = a.entity_id
    LEFT JOIN managers mx ON a.entity_type = 'Manager' AND mx.id = a.entity_id
    LEFT JOIN business_areas bx ON a.entity_type = 'BusinessArea' AND bx.id = a.entity_id
    LEFT JOIN financial_types tx ON a.entity_type = 'FinancialType' AND tx.id = a.entity_id
    ORDER BY 2, 3, 4, 6;

-- Grouping of references to rows that no longer exist (or never had a table).
SELECT 'orphan_audit_org', g FROM (SELECT GROUP_CONCAT(CONCAT(a.timestamp, '|', a.action, '|', a.details) ORDER BY a.timestamp, a.details SEPARATOR ' ; ') AS g
    FROM audit_logs a WHERE a.org_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM organizations o WHERE o.id = a.org_id)
    GROUP BY a.org_id) x ORDER BY g;
SELECT 'audit_entity_groups', entity_type, g FROM (SELECT a.entity_type,
       GROUP_CONCAT(CONCAT(a.timestamp, '|', a.action, '|', a.details) ORDER BY a.timestamp, a.details SEPARATOR ' ; ') AS g
    FROM audit_logs a WHERE a.entity_id IS NOT NULL GROUP BY a.entity_type, a.entity_id) x ORDER BY entity_type, g;
SELECT 'orphan_notification_org', g FROM (SELECT GROUP_CONCAT(CONCAT(n.created_at, '|', n.title) ORDER BY n.created_at, n.title SEPARATOR ' ; ') AS g
    FROM notifications n WHERE NOT EXISTS (SELECT 1 FROM organizations o WHERE o.id = n.org_id) GROUP BY n.org_id) x ORDER BY g;
SELECT 'orphan_terms', g FROM (SELECT GROUP_CONCAT(CONCAT(a.accepted_at, '|', a.username) ORDER BY a.accepted_at SEPARATOR ' ; ') AS g
    FROM terms_acceptances a WHERE NOT EXISTS (SELECT 1 FROM organizations o WHERE o.id = a.organization_id)
    GROUP BY a.organization_id) x ORDER BY g;
