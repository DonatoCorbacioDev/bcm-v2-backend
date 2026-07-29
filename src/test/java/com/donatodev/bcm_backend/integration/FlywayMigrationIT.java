package com.donatodev.bcm_backend.integration;

import java.util.List;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * Proves the full migration history (V1-V32) applies cleanly to real MySQL
 * 8.0 and that every JPA entity mapping validates against the resulting
 * schema ({@code ddl-auto=validate} in the base class) — something the H2
 * "MySQL mode" used by the fast unit suite cannot guarantee, since H2 is not
 * MySQL and has papered over real dialect differences before (see e.g.
 * V27, which had to widen a native MySQL ENUM column H2 never enforced).
 */
@SpringBootTest
@DisplayName("Integration Test: Flyway migrations against real MySQL")
class FlywayMigrationIT extends AbstractMySQLIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Spring context loads: all migrations applied and entity mappings match the real schema")
    void contextLoads() {
        // If Flyway failed to apply a migration, or a JPA entity no longer
        // matches the real MySQL schema (ddl-auto=validate), context startup
        // itself throws — this test passing IS the assertion.
    }

    @Test
    @DisplayName("flyway_schema_history: all 32 migrations recorded as successful, none pending")
    void allMigrationsAppliedSuccessfully() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<Boolean> successFlags = jdbc.queryForList(
                "SELECT success FROM flyway_schema_history ORDER BY installed_rank", Boolean.class);

        assertTrue(successFlags.size() >= 32,
                "Expected at least 32 applied migrations, found " + successFlags.size());
        assertFalse(successFlags.contains(false), "At least one migration is recorded as failed");

        Integer maxVersion = jdbc.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE version IS NOT NULL",
                Integer.class);
        assertEquals(32, maxVersion, "Highest applied migration version should be V32");
    }
}
