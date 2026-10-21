-- Proof of which Terms of Service an organization agreed to, and when: the
-- version string matches the text published at /termini (LegalTerms /
-- lib/legal.ts), stamped at self-service registration together with the
-- separate approval of the onerous clauses (arts. 1341-1342 c.c.). Who
-- accepted is in audit_logs (TERMS_ACCEPTED). NULL for organizations created
-- before Terms existed (seeded/demo orgs, earlier registrations).
ALTER TABLE organizations
    ADD COLUMN terms_version VARCHAR(20),
    ADD COLUMN terms_accepted_at TIMESTAMP NULL;
