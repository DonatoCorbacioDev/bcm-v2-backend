-- invite_token.manager_id had no ON DELETE clause (RESTRICT by default),
-- and ManagerService.deleteManager() never cleans it up — same bug shape as
-- contract_history/modified_by (V33/V34) and financial_values/
-- contract_manager (V35), on the last remaining child table missed. Any
-- manager who was ever invited (the standard onboarding flow) could never
-- be deleted. Unlike modified_by, an invite token has no audit value after
-- the fact (write-once, consumed-once, no endpoint ever lists past
-- invites), so CASCADE is correct here, same as V35.
ALTER TABLE invite_token
    DROP FOREIGN KEY fk_invite_manager,
    ADD CONSTRAINT fk_invite_token_manager FOREIGN KEY (manager_id) REFERENCES managers(id) ON DELETE CASCADE;
