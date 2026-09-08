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
 * Proves the full migration history (V1-V39) applies cleanly to real MySQL
 * 8.0 and that every JPA entity mapping validates against the resulting
 * schema ({@code ddl-auto=validate} in the base class) — something the H2
 * "MySQL mode" used by the fast unit suite cannot guarantee, since H2 is not
 * MySQL and has papered over real dialect differences before (see e.g.
 * V27, which had to widen a native MySQL ENUM column H2 never enforced;
 * V37, which introduced a collation mismatch between its explicit
 * utf8mb4_unicode_ci and the server default that only reproduces against a
 * real MySQL server — see AbstractMySQLIntegrationTest).
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
    @DisplayName("flyway_schema_history: all 39 migrations recorded as successful, none pending")
    void allMigrationsAppliedSuccessfully() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<Boolean> successFlags = jdbc.queryForList(
                "SELECT success FROM flyway_schema_history ORDER BY installed_rank", Boolean.class);

        assertTrue(successFlags.size() >= 39,
                "Expected at least 39 applied migrations, found " + successFlags.size());
        assertFalse(successFlags.contains(false), "At least one migration is recorded as failed");

        Integer maxVersion = jdbc.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE version IS NOT NULL",
                Integer.class);
        assertEquals(39, maxVersion, "Highest applied migration version should be V39");
    }

    @Test
    @DisplayName("Deleting a contract cascades to its contract_history rows instead of failing with a FK conflict")
    void deletingContractCascadesToHistory() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO organizations (id, name, slug) VALUES (9001, 'FK Cascade Test Org', 'fk-cascade-test-org')");
        jdbc.update("INSERT INTO managers (id, first_name, last_name, email, organization_id) "
                + "VALUES (9001, 'Test', 'Manager', 'fk-cascade-test@example.com', 9001)");
        jdbc.update("INSERT INTO counterparties (id, name, type, organization_id) "
                + "VALUES (9001, 'Test Customer', 'CUSTOMER', 9001)");
        jdbc.update("INSERT INTO contracts (id, counterparty_id, contract_number, manager_id, start_date, status, organization_id) "
                + "VALUES (9001, 9001, 'FK-CASCADE-001', 9001, '2026-01-01', 'ACTIVE', 9001)");
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
        jdbc.update("DELETE FROM counterparties WHERE id = 9001");
        jdbc.update("DELETE FROM organizations WHERE id = 9001");
    }

    @Test
    @DisplayName("Deleting a user nullifies contract_history.modified_by instead of failing with a FK conflict")
    void deletingUserNullifiesHistoryModifiedBy() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO organizations (id, name, slug) VALUES (9002, 'FK SetNull Test Org', 'fk-setnull-test-org')");
        jdbc.update("INSERT INTO managers (id, first_name, last_name, email, organization_id) "
                + "VALUES (9002, 'Test', 'Manager', 'fk-setnull-test@example.com', 9002)");
        jdbc.update("INSERT INTO counterparties (id, name, type, organization_id) "
                + "VALUES (9002, 'Test Customer', 'CUSTOMER', 9002)");
        jdbc.update("INSERT INTO contracts (id, counterparty_id, contract_number, manager_id, start_date, status, organization_id) "
                + "VALUES (9002, 9002, 'FK-SETNULL-001', 9002, '2026-01-01', 'ACTIVE', 9002)");
        jdbc.update("INSERT INTO roles (id, role) VALUES (9002, 'FK_SETNULL_TEST_ROLE')");
        jdbc.update("INSERT INTO users (id, username, password_hash, manager_id, role_id, organization_id) "
                + "VALUES (9002, 'fk-setnull-test-user', 'x', 9002, 9002, 9002)");
        jdbc.update("INSERT INTO contract_history (id, contract_id, modified_by, previous_status, new_status) "
                + "VALUES (9002, 9002, 9002, 'ACTIVE', 'EXPIRED')");

        jdbc.update("DELETE FROM users WHERE id = 9002");

        Long modifiedBy = jdbc.queryForObject(
                "SELECT modified_by FROM contract_history WHERE id = 9002", Long.class);
        assertEquals(null, modifiedBy, "modified_by should be nulled, not block the user deletion");

        jdbc.update("DELETE FROM contract_history WHERE id = 9002");
        jdbc.update("DELETE FROM contracts WHERE id = 9002");
        jdbc.update("DELETE FROM managers WHERE id = 9002");
        jdbc.update("DELETE FROM roles WHERE id = 9002");
        jdbc.update("DELETE FROM counterparties WHERE id = 9002");
        jdbc.update("DELETE FROM organizations WHERE id = 9002");
    }

    @Test
    @DisplayName("Deleting a contract cascades to its financial_values and contract_manager rows instead of failing with a FK conflict")
    void deletingContractCascadesToFinancialValuesAndCollaborators() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO organizations (id, name, slug) VALUES (9003, 'FK Cascade Test Org 2', 'fk-cascade-test-org-2')");
        jdbc.update("INSERT INTO managers (id, first_name, last_name, email, organization_id) "
                + "VALUES (9003, 'Test', 'Manager', 'fk-cascade-test-2@example.com', 9003)");
        jdbc.update("INSERT INTO counterparties (id, name, type, organization_id) "
                + "VALUES (9003, 'Test Customer', 'CUSTOMER', 9003)");
        jdbc.update("INSERT INTO contracts (id, counterparty_id, contract_number, manager_id, start_date, status, organization_id) "
                + "VALUES (9003, 9003, 'FK-CASCADE-002', 9003, '2026-01-01', 'ACTIVE', 9003)");
        jdbc.update("INSERT INTO financial_values (id, month_value, year_value, financial_amount, contract_id, organization_id) "
                + "VALUES (9003, 1, 2026, 1000.0, 9003, 9003)");
        jdbc.update("INSERT INTO contract_manager (contract_id, manager_id) VALUES (9003, 9003)");

        jdbc.update("DELETE FROM contracts WHERE id = 9003");

        Integer remainingFinancialValues = jdbc.queryForObject(
                "SELECT COUNT(*) FROM financial_values WHERE contract_id = 9003", Integer.class);
        assertEquals(0, remainingFinancialValues, "financial_values rows should cascade-delete with their contract");

        Integer remainingCollaborators = jdbc.queryForObject(
                "SELECT COUNT(*) FROM contract_manager WHERE contract_id = 9003", Integer.class);
        assertEquals(0, remainingCollaborators, "contract_manager rows should cascade-delete with their contract");

        jdbc.update("DELETE FROM managers WHERE id = 9003");
        jdbc.update("DELETE FROM counterparties WHERE id = 9003");
        jdbc.update("DELETE FROM organizations WHERE id = 9003");
    }

    @Test
    @DisplayName("Deleting a manager cascades to its invite_token rows instead of failing with a FK conflict")
    void deletingManagerCascadesToInviteToken() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO organizations (id, name, slug) VALUES (9004, 'FK Cascade Test Org 3', 'fk-cascade-test-org-3')");
        jdbc.update("INSERT INTO managers (id, first_name, last_name, email, organization_id) "
                + "VALUES (9004, 'Test', 'Manager', 'fk-cascade-test-3@example.com', 9004)");
        jdbc.update("INSERT INTO invite_token (id, token, expiry_date, username, role, manager_id, used) "
                + "VALUES (9004, 'fk-cascade-test-token', '2027-01-01 00:00:00', 'fk-cascade-test-user', 'MANAGER', 9004, false)");

        jdbc.update("DELETE FROM managers WHERE id = 9004");

        Integer remainingInviteTokens = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invite_token WHERE manager_id = 9004", Integer.class);
        assertEquals(0, remainingInviteTokens, "invite_token rows should cascade-delete with their manager");

        jdbc.update("DELETE FROM organizations WHERE id = 9004");
    }
}
