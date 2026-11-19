/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What Flyway does when the backend starts. By default it applies the
 * migrations, as a developer running the backend alone expects. Where the
 * migrations run on their own beforehand ({@code DatabaseMigration}, the
 * {@code migrate} container), {@code app.flyway.migrate-on-start=false} makes
 * it only check them: every migration in the jar applied, none changed. That
 * needs nothing but reading {@code flyway_schema_history}, so it runs with the
 * account the backend runs with, which can change rows and not the schema; a
 * missing migration stops the backend instead of running it with less than
 * it needs (OWASP Database Security, separate application and migration accounts).
 */
@Configuration
public class FlywayStartupConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayStartupConfig.class);

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${app.flyway.migrate-on-start:true}") boolean migrateOnStart) {
        return flyway -> {
            if (migrateOnStart) {
                flyway.migrate();
            } else {
                // Throws on a pending or changed migration: the backend does not start.
                flyway.validate();
                logger.info("Schema checked, migrations applied beforehand by the migrate step");
            }
        };
    }
}
