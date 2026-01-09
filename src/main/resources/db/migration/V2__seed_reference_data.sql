-- V2__seed_reference_data.sql
-- Reference data required for application initialization

-- System roles (required)
INSERT INTO roles (role) VALUES ('ADMIN'), ('MANAGER')
AS new_roles
ON DUPLICATE KEY UPDATE role=new_roles.role;

-- Standard business areas
INSERT INTO business_areas (name, description) VALUES
('IT Services', 'Information Technology and Software Development'),
('Finance', 'Financial consulting and accounting'),
('Construction', 'Building and infrastructure development')
AS new_areas
ON DUPLICATE KEY UPDATE name=new_areas.name;

-- Standard financial types
INSERT INTO financial_types (name, description) VALUES
('SALES', 'Revenue from the sale of products or services'),
('COSTS', 'Operating expenses and business costs'),
('NR', 'Not classified or not relevant for financial reporting')
AS new_types
ON DUPLICATE KEY UPDATE name=new_types.name;