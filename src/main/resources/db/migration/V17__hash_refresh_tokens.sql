-- Tokens were previously stored in plain text.
-- They cannot be re-hashed (SHA-256 is one-way), so we clear the table.
-- All active sessions are invalidated; users must log in again.
TRUNCATE TABLE refresh_tokens;
