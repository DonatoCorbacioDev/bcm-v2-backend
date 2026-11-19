package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * OWASP Database Security, application and migration accounts kept apart,
 * against real MySQL. The schema is built by the owner (here by the test
 * context, in production by the migrate container); the account the
 * application runs with can check the migrations but not apply one, and a
 * migration it has not seen stops the check, so the backend does not start
 * half-upgraded.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Integration Test: migrations run as the owner, the application only checks them")
class MigrationSeparationIT extends AbstractMySQLIntegrationTest {

    private static final String PENDING = "classpath:db/pending";

    private final JdbcTemplate jdbc;

    @Autowired
    MigrationSeparationIT(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    private static Flyway asApplication(String... locations) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), APP_USER, APP_PASSWORD)
                .locations(locations)
                .load();
    }

    @Test
    @DisplayName("the application's account checks the applied migrations")
    void theApplicationAccountValidates() {
        asApplication("classpath:db/migration").validate();
    }

    @Test
    @DisplayName("the application's account cannot apply a migration, and nothing changes")
    void theApplicationAccountCannotMigrate() {
        assertThatThrownBy(() -> asApplication("classpath:db/migration", PENDING).migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("denied");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'pending_check'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("a migration not yet applied stops the check: the backend would not start")
    void aPendingMigrationFailsTheCheck() {
        assertThatThrownBy(() -> asApplication("classpath:db/migration", PENDING).validate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("the migration step, as the owner, finds the schema up to date")
    void theMigrationStepIsIdempotent() {
        var result = com.donatodev.bcm_backend.migration.DatabaseMigrationAccess.migrate(Map.of(
                "DB_URL", MYSQL.getJdbcUrl(),
                "DB_USERNAME", MYSQL.getUsername(),
                "DB_PASSWORD", MYSQL.getPassword()));

        assertThat(result.migrationsExecuted).isZero();
        assertThat(result.success).isTrue();
    }
}
