-- Account lockout after repeated failed login attempts. The existing
-- RateLimitingFilter throttles /auth/login by client IP only, so a
-- distributed or IP-rotating attacker could still brute-force a single
-- known username unthrottled. This adds a second, per-account control.
ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until DATETIME NULL;
