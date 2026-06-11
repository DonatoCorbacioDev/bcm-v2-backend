-- V14__neutralize_default_admin.sql
-- V4 shipped a default 'admin' account with a known, fixed password hash
-- (password "admin123"). Disable that account wherever it is still using
-- the original hash, so the well-known credentials can no longer be used
-- to log in on any environment that has already run V4/V6.
--
-- This is a no-op for environments where the password has already been
-- rotated (the WHERE clause won't match).
--
-- Recovery: an operator with DB access can re-enable the account with
-- `UPDATE users SET verified = TRUE WHERE username = 'admin'` and then
-- complete a password reset via the forgot-password flow.

UPDATE users
SET verified = FALSE
WHERE username = 'admin'
  AND password_hash = '$2a$12$siyVMp6Lw5gcRw9fAh5GHO1aIfr17jCGD6rC/UdMx73M2voAG.Y0W';
