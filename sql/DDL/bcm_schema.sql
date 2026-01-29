-- Crea il database principale
CREATE DATABASE bcm;

-- Seleziona il database per le operazioni successive
USE bcm;

-- Crea la tabella delle aree di business (settori aziendali)
CREATE TABLE business_areas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT
);

-- Crea la tabella dei tipi finanziari (categorie di entrate/spese)
CREATE TABLE financial_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT
);

-- Crea la tabella dei manager (responsabili contrattuali)
CREATE TABLE managers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone_number VARCHAR(20),
    department VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Crea la tabella dei contratti
CREATE TABLE contracts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    contract_number VARCHAR(255) UNIQUE NOT NULL,
    wbs_code VARCHAR(50),
    project_name VARCHAR(100),
    area_id BIGINT,
    manager_id BIGINT,
    start_date DATE NOT NULL,
    end_date DATE,
    status ENUM('ACTIVE', 'EXPIRED', 'CANCELLED') NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (area_id) REFERENCES business_areas(id),
    FOREIGN KEY (manager_id) REFERENCES managers(id) ON DELETE SET NULL
);

-- Crea la tabella di relazione contratti-manager (molti a molti)
CREATE TABLE contract_manager (
    contract_id BIGINT,
    manager_id BIGINT,
    PRIMARY KEY (contract_id, manager_id),
    FOREIGN KEY (contract_id) REFERENCES contracts(id),
    FOREIGN KEY (manager_id) REFERENCES managers(id)
);

-- Crea la tabella dei valori finanziari associati ai contratti
CREATE TABLE financial_values (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    month_value INT NOT NULL,
    year_value INT NOT NULL,
    financial_amount DOUBLE NOT NULL,
    financial_type_id BIGINT,
    area_id BIGINT,
    contract_id BIGINT,
    FOREIGN KEY (financial_type_id) REFERENCES financial_types(id),
    FOREIGN KEY (area_id) REFERENCES business_areas(id),
    FOREIGN KEY (contract_id) REFERENCES contracts(id)
);

-- Crea la tabella dei ruoli utente
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role VARCHAR(255) NOT NULL UNIQUE
);

-- Crea la tabella degli utenti con credenziali di accesso
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    manager_id BIGINT NOT NULL UNIQUE,
    role_id BIGINT NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (manager_id) REFERENCES managers(id),
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- Crea la tabella dello storico delle modifiche dei contratti
CREATE TABLE contract_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    modified_by BIGINT NOT NULL,
    modification_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    previous_status ENUM('ACTIVE', 'EXPIRED', 'CANCELLED'),
    new_status ENUM('ACTIVE', 'EXPIRED', 'CANCELLED'),
    FOREIGN KEY (contract_id) REFERENCES contracts(id),
    FOREIGN KEY (modified_by) REFERENCES users(id)
);

-- Crea la tabella dei token di verifica email
CREATE TABLE verification_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date DATETIME NOT NULL,
    user_id BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE invite_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date DATETIME NOT NULL,
    username VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,           
    manager_id BIGINT NOT NULL,          
    used BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_invite_manager FOREIGN KEY (manager_id) REFERENCES managers(id)
);

-- Query di verifica delle tabelle create
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