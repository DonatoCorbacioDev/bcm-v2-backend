-- V4__create_admin_user.sql
-- Creates default admin user for system initialization

-- Create admin manager (id=1)
INSERT INTO managers (id, first_name, last_name, email, phone_number, department)
VALUES (1, 'System', 'Administrator', 'admin@example.com', '+1234567890', 'IT')
AS new_manager
ON DUPLICATE KEY UPDATE 
    first_name = new_manager.first_name,
    last_name = new_manager.last_name,
    email = new_manager.email;

-- Create admin user with BCrypt password hash for "admin123"
-- Password: admin123
-- Account is pre-verified for immediate access
INSERT INTO users (username, password_hash, manager_id, role_id, verified)
SELECT 'admin@example.com', '$2a$12$siyVMp6Lw5gcRw9fAh5GHO1aIfr17jCGD6rC/UdMx73M2voAG.Y0W', 1, r.id, TRUE
FROM roles r
WHERE r.role = 'ADMIN'
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    manager_id = VALUES(manager_id),
    verified = VALUES(verified);