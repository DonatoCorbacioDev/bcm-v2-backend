-- V5__add_created_at_to_users.sql
-- Add created_at timestamp to users table for audit trail

ALTER TABLE users 
ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;