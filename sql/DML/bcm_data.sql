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