-- V6__rename_admin_username.sql
-- Rename admin user to a simple username (no email format)
UPDATE users SET username = 'admin' WHERE username = 'admin@example.com';
