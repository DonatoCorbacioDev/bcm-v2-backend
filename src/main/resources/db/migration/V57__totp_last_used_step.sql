-- The TOTP time step of the last code accepted at login (OWASP API2): a code
-- is accepted only for a later step, so the same 6 digits can't log in twice
-- while they are still valid. NULL until the first 2FA login.
ALTER TABLE users ADD COLUMN totp_last_used_step BIGINT NULL;
