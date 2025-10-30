-- Seleziona il database da utilizzare
USE bcm;

-- Inserimento dati per le aree di business
INSERT INTO business_areas (name, description) VALUES
('IT Services', 'Information Technology and Software Development'),
('Finance', 'Financial consulting and accounting'),
('Construction', 'Building and infrastructure development');

-- Inserimento dati per i tipi finanziari
INSERT INTO financial_types (name, description) VALUES
('SALES', 'Revenue from the sale of products or services'),
('COSTS', 'Operating expenses and business costs'),
('NR', 'Not classified or not relevant for financial reporting');

-- Inserimento dati dei manager
INSERT INTO managers (first_name, last_name, email, phone_number, department) VALUES
('Donato', 'Ferrari', 'donato@example.com', '+393931234567', 'IT'),
('Alessandra', 'Verdi', 'alessandra@example.com', '+393331237654', 'HR');

-- Inserimento dati dei contratti aziendali
INSERT INTO contracts (customer_name, contract_number, wbs_code, project_name, area_id, manager_id, start_date, end_date, status) VALUES
('ABC Corp', 'CNT-001', 'WBS-1001', 'Cloud Migration', 1, 1, '2025-01-01', '2025-12-31', 'ACTIVE'),
('XYZ Ltd', 'CNT-002', 'WBS-1002', 'ERP Implementation', 2, 2, '2025-02-01', '2025-08-31', 'ACTIVE');

-- Associazione contratti-manager
INSERT INTO contract_manager (contract_id, manager_id) VALUES
(1, 1),
(2, 2);

-- Inserimento dati finanziari
INSERT INTO financial_values (month_value, year_value, financial_amount, financial_type_id, area_id, contract_id) VALUES
(1, 2025, 50000, 1, 1, 1),
(2, 2025, 12000, 2, 2, 2);

-- Inserimento dati dei ruoli
INSERT INTO roles (role) VALUES
('ADMIN'),
('MANAGER');

-- Inserimento dati utenti del sistema
INSERT INTO users (username, password_hash, manager_id, role_id) VALUES
('Donato', '$2y$12$ExampleHashedPassword', 1, 1),
('Alessandra', '$2y$12$ExampleHashedPassword', 2, 2);

-- Inserimento dati per lo storico delle modifiche ai contratti
INSERT INTO contract_history (contract_id, modified_by, previous_status, new_status) VALUES
(1, 1, 'ACTIVE', 'EXPIRED');

-- Controllo dei dati inseriti
SELECT * FROM business_areas;
SELECT * FROM financial_types;
SELECT * FROM managers;
SELECT * FROM contracts;
SELECT * FROM contract_manager;
SELECT * FROM financial_values;
SELECT * FROM roles;
SELECT * FROM users;
SELECT * FROM contract_history;
SELECT * FROM verification_token;

-- Reset dell'auto-incremento delle tabelle a 1
ALTER TABLE business_areas AUTO_INCREMENT = 1;
ALTER TABLE financial_types AUTO_INCREMENT = 1;
ALTER TABLE managers AUTO_INCREMENT = 1;
ALTER TABLE contracts AUTO_INCREMENT = 1;
ALTER TABLE financial_values AUTO_INCREMENT = 1;
ALTER TABLE roles AUTO_INCREMENT = 1;
ALTER TABLE users AUTO_INCREMENT = 1;
ALTER TABLE contract_history AUTO_INCREMENT = 1;
ALTER TABLE verification_token AUTO_INCREMENT = 1;

-- Cancellazione dei dati inseriti (senza eliminare le tabelle)
DELETE FROM contract_history;
DELETE FROM users;
DELETE FROM roles;
DELETE FROM financial_values;
DELETE FROM contract_manager;
DELETE FROM contracts;
DELETE FROM managers;
DELETE FROM financial_types;
DELETE FROM business_areas;
DELETE FROM verification_token;

-- Eliminazione delle tabelle
DROP TABLE IF EXISTS contract_history;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS financial_values;
DROP TABLE IF EXISTS contract_manager;
DROP TABLE IF EXISTS contracts;
DROP TABLE IF EXISTS managers;
DROP TABLE IF EXISTS financial_types;
DROP TABLE IF EXISTS business_areas;
DROP TABLE IF EXISTS verification_token;