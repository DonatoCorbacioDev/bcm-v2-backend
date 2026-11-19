-- Test only (MigrationSeparationIT): a migration the database has not seen,
-- to prove the application's account cannot apply it and the check stops on it.
CREATE TABLE pending_check (id INT PRIMARY KEY);
