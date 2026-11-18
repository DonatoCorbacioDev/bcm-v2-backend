-- The audit log is evidence (OWASP Logging Cheat Sheet: protect logs from
-- tampering): once written, a row cannot be changed, not even by the account
-- the application runs with, which may update every other table. That
-- account has no TRIGGER privilege, so it cannot drop this either; only the
-- schema owner (Flyway) can. Deleting stays allowed: the retention job
-- removes rows past 180 days, and deleting an organization removes its rows
-- (GDPR). A later migration that must rewrite rows drops this trigger first
-- and creates it again after.
--
-- Creating a trigger without SUPER needs log_bin_trust_function_creators on
-- the server (docker-compose.yml; the integration tests' container).
CREATE TRIGGER audit_logs_no_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs rows cannot be changed';
