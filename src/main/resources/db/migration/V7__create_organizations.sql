-- V7__create_organizations.sql
-- Create organizations table for multi-tenancy support

CREATE TABLE organizations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    subscription_tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Default organization for all existing data
INSERT INTO organizations (name, slug, subscription_tier)
VALUES ('Default Organization', 'default', 'FREE');
