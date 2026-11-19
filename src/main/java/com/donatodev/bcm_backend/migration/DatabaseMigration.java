/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Applies the schema migrations and exits: the one place the schema owner's
 * account is used (OWASP Database Security, separate the application's account
 * from the one that changes the schema). It runs as its own short-lived
 * container ({@code migrate} in docker-compose.yml), from this same image,
 * before the backend starts; the backend then holds only the account that
 * reads and writes rows, and only checks that no migration is missing
 * ({@code FlywayStartupConfig}). A compromised backend can no longer drop or
 * alter a table, nor read the owner's password.
 * <p>
 * Plain Flyway, no Spring context: nothing else starts (no web server, no
 * scheduler, no mail). Run with
 * {@code java -Dloader.main=com.donatodev.bcm_backend.migration.DatabaseMigration
 * -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher}.
 * <p>
 * Settings from the environment: {@code DB_URL} (JDBC), {@code DB_USERNAME},
 * and the password from {@code DB_PASSWORD} or, in production, the Docker
 * secret file of that name ({@code SECRETS_DIR}, default {@code /run/secrets}).
 */
public final class DatabaseMigration {

    static final String LOCATION = "classpath:db/migration";

    private DatabaseMigration() {
    }

    public static void main(String[] args) {
        MigrateResult result = migrate(System.getenv());
        // Exit code 0 lets the backend start (depends_on: service_completed_successfully).
        System.out.printf("Schema migrations: %d applied, schema at version %s%n",
                result.migrationsExecuted, result.targetSchemaVersion == null ? "unchanged" : result.targetSchemaVersion);
    }

    /** Migrates the schema the environment names; throws (exit code 1) on any failure. */
    static MigrateResult migrate(Map<String, String> env) {
        return Flyway.configure()
                .dataSource(required(env, "DB_URL"), required(env, "DB_USERNAME"), password(env))
                .locations(LOCATION)
                // Production's settings (application-prod.properties): no baseline over an
                // existing schema, every applied migration checked against its file, in order.
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .outOfOrder(false)
                .load()
                .migrate();
    }

    static String password(Map<String, String> env) {
        String value = env.get("DB_PASSWORD");
        if (value != null && !value.isEmpty()) {
            return value;
        }
        Path file = Path.of(env.getOrDefault("SECRETS_DIR", "/run/secrets"), "DB_PASSWORD");
        try {
            return Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("No DB_PASSWORD: neither the variable nor the secret file is there", e);
        }
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
