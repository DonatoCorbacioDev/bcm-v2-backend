-- Self-service registration now completes only after the admin types a
-- one-time code emailed to them. The code proves the address is real and,
-- per Cass. ord. 20945/2026, acts as the simple electronic signature of the
-- specific approval of the onerous clauses (arts. 1341-1342 c.c.): a bare
-- checkbox is not enough. Until confirmed, the request lives here — never as
-- an organization or user — with the password already hashed; rows expire
-- after a few minutes and are purged hourly.
CREATE TABLE pending_registrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    organization_name VARCHAR(255) NOT NULL,
    admin_first_name VARCHAR(255) NOT NULL,
    admin_last_name VARCHAR(255) NOT NULL,
    admin_email VARCHAR(255) NOT NULL,
    admin_username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    terms_version VARCHAR(20) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    codes_sent INT NOT NULL DEFAULT 1,
    code_sent_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    request_ip VARCHAR(45),
    request_user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pending_registrations_email (admin_email),
    INDEX idx_pending_registrations_expires (expires_at)
);

-- Evidence of each Terms acceptance, kept apart from audit_logs on purpose:
-- audit_logs is purged after 180 days, while this proof must last as long as
-- a claim under the contract can (10 years, art. 2946 c.c.). No foreign keys
-- for the same reason: the evidence has to outlive the organization and the
-- user it refers to, so their identifiers are copied, not referenced.
CREATE TABLE terms_acceptances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    organization_name VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    username VARCHAR(255) NOT NULL,
    signer_email VARCHAR(255) NOT NULL,
    terms_version VARCHAR(20) NOT NULL,
    onerous_clauses VARCHAR(100) NOT NULL,
    professional_use_declared BOOLEAN NOT NULL,
    signature_method VARCHAR(20) NOT NULL,
    code_sent_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP NOT NULL,
    request_ip VARCHAR(45),
    request_user_agent VARCHAR(255),
    confirm_ip VARCHAR(45),
    confirm_user_agent VARCHAR(255),
    INDEX idx_terms_acceptances_organization (organization_id)
);
