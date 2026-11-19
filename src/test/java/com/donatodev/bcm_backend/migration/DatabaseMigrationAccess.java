package com.donatodev.bcm_backend.migration;

import java.util.Map;

import org.flywaydb.core.api.output.MigrateResult;

/** Lets integration tests in other packages run the migration step as production does. */
public final class DatabaseMigrationAccess {

    private DatabaseMigrationAccess() {
    }

    public static MigrateResult migrate(Map<String, String> env) {
        return DatabaseMigration.migrate(env);
    }
}
