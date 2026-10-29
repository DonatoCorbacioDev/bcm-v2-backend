-- Self-service deletion of an organization (GDPR art. 17, Terms sez. 13).
-- An admin asks for it with password + organization name, then confirms with
-- a code emailed to them; the deletion is then scheduled 30 days ahead, so the
-- data stay exportable and a mistake (or a stolen session) can be undone.
-- deletion_scheduled_for NULL means no deletion is pending.
ALTER TABLE organizations
    ADD COLUMN deletion_scheduled_for TIMESTAMP NULL,
    ADD COLUMN deletion_requested_at TIMESTAMP NULL,
    ADD COLUMN deletion_requested_by VARCHAR(255) NULL;

-- The emailed confirmation code of a deletion request, at most one per
-- organization. Same rules as pending_registrations: only the SHA-256 of the
-- code is stored, it expires after a few minutes, and wrong attempts are
-- counted. Removed with the organization.
CREATE TABLE organization_deletion_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    code_sent_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_org_deletion_codes_org FOREIGN KEY (organization_id)
        REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_org_deletion_codes_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
);
