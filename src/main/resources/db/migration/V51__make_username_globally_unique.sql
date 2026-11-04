-- V8 made usernames unique per organization (username, organization_id), but
-- every path that creates or renames a user (registration, invite, profile
-- update) has since checked existsByUsername across all organizations, and
-- most lookups resolve a user by username alone. The database now enforces
-- the rule the application already applies, so two organizations can't end
-- up with the same username through concurrent registrations, which would
-- break every username-only lookup for both users.
--
-- Fails, and so stops the deploy, if duplicates exist: they must be resolved
-- by hand first. Checked on production before release: none.

ALTER TABLE users DROP INDEX uq_usr_name_org;
ALTER TABLE users ADD CONSTRAINT uq_usr_username UNIQUE (username);
