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
 * Proves the full migration history (V1-V33) applies cleanly to real MySQL
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
    @DisplayName("flyway_schema_history: all 33 migrations recorded as successful, none pending")
    void allMigrationsAppliedSuccessfully() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<Boolean> successFlags = jdbc.queryForList(
                "SELECT success FROM flyway_schema_history ORDER BY installed_rank", Boolean.class);

        assertTrue(successFlags.size() >= 33,
                "Expected at least 33 applied migrations, found " + successFlags.size());
        assertFalse(successFlags.contains(false), "At least one migration is recorded as failed");

        Integer maxVersion = jdbc.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE version IS NOT NULL",
                Integer.class);
        assertEquals(33, maxVersion, "Highest applied migration version should be V33");
    }

    @Test
    @DisplayName("Deleting a contract cascades to its contract_history rows instead of failing with a FK conflict")
    void deletingContractCascadesToHistory() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO organizations (id, name, slug) VALUES (9001, 'FK Cascade Test Org', 'fk-cascade-test-org')");
        jdbc.update("INSERT INTO managers (id, first_name, last_name, email, organization_id) "
                + "VALUES (9001, 'Test', 'Manager', 'fk-cascade-test@example.com', 9001)");
        jdbc.update("INSERT INTO contracts (id, customer_name, contract_number, manager_id, start_date, status, organization_id) "
                + "VALUES (9001, 'Test Customer', 'FK-CASCADE-001', 9001, '2026-01-01', 'ACTIVE', 9001)");
        jdbc.update("INSERT INTO roles (id, role) VALUES (9001, 'FK_CASCADE_TEST_ROLE')");
        jdbc.update("INSERT INTO users (id, username, password_hash, manager_id, role_id, organization_id) "
                + "VALUES (9001, 'fk-cascade-test-user', 'x', 9001, 9001, 9001)");
        jdbc.update("INSERT INTO contract_history (contract_id, modified_by, previous_status, new_status) "
                + "VALUES (9001, 9001, 'ACTIVE', 'EXPIRED')");

        jdbc.update("DELETE FROM contracts WHERE id = 9001");

        Integer remainingHistory = jdbc.queryForObject(
                "SELECT COUNT(*) FROM contract_history WHERE contract_id = 9001", Integer.class);
        assertEquals(0, remainingHistory, "contract_history rows should cascade-delete with their contract");

        jdbc.update("DELETE FROM users WHERE id = 9001");
        jdbc.update("DELETE FROM managers WHERE id = 9001");
        jdbc.update("DELETE FROM roles WHERE id = 9001");
        jdbc.update("DELETE FROM organizations WHERE id = 9001");
    }
}
