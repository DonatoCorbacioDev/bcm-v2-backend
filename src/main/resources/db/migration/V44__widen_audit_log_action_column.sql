-- The original VARCHAR(20) fit only the generic CREATE/UPDATE/DELETE actions
-- inferred by AuditAspect. The new IBAN-verification audit events logged
-- explicitly by CounterpartyService/SepaPaymentService (e.g.
-- 'SEPA_IBAN_MISMATCH_BLOCKED') are longer and more specific on purpose --
-- widening here instead of truncating them keeps the audit trail readable.
ALTER TABLE audit_logs MODIFY COLUMN action VARCHAR(40) NOT NULL;
