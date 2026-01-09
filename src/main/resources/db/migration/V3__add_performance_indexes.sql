-- V3__add_performance_indexes.sql
-- Performance indexes for optimized queries

CREATE INDEX idx_contracts_status ON contracts(status);
CREATE INDEX idx_contracts_manager_id ON contracts(manager_id);
CREATE INDEX idx_contracts_area_id ON contracts(area_id);
CREATE INDEX idx_contracts_end_date ON contracts(end_date);
CREATE INDEX idx_financial_values_contract_id ON financial_values(contract_id);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_verification_token_token ON verification_token(token);
CREATE INDEX idx_invite_token_token ON invite_token(token);
CREATE INDEX idx_contract_manager_manager_id ON contract_manager(manager_id);
CREATE INDEX idx_password_reset_token_token ON password_reset_token(token);