package com.donatodev.bcm_backend.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** At startup the backend applies the migrations, or, where they ran beforehand, only checks them. */
class FlywayStartupConfigTest {

    private final FlywayStartupConfig config = new FlywayStartupConfig();

    @Test
    @DisplayName("by default it applies them (a developer running the backend alone)")
    void migratesByDefault() {
        Flyway flyway = mock(Flyway.class);

        config.flywayMigrationStrategy(true).migrate(flyway);

        verify(flyway).migrate();
        verify(flyway, never()).validate();
    }

    @Test
    @DisplayName("with migrate-on-start off it only checks them, changing nothing")
    void onlyValidatesWhenTheMigrateStepRanBefore() {
        Flyway flyway = mock(Flyway.class);

        config.flywayMigrationStrategy(false).migrate(flyway);

        verify(flyway).validate();
        verify(flyway, never()).migrate();
    }
}
