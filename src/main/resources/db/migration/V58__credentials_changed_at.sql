-- When the user's credentials last changed (password, 2FA), as epoch seconds
-- (OWASP API2). An access token issued before it is refused, so a stolen
-- session ends at the change instead of when its token expires. NULL: never
-- changed since this column exists, every unexpired token is still accepted.
ALTER TABLE users ADD COLUMN credentials_changed_at BIGINT NULL;
