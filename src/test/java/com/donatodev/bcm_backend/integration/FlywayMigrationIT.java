package com.donatodev.bcm_backend.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * Proves the full migration history (V1-V42) applies cleanly to real MySQL
 * 8.0 and that every JPA entity mapping validates against the resulting
 * schema ({@code ddl-auto=validate} in the base class) — something the H2
 * "MySQL mode" used by the fast unit suite cannot guarantee, since H2 is not
 * MySQL and has papered over real dialect differences before (see e.g.
 * V27, which had to widen a native MySQL ENUM column H2 never enforced;
 * V37, which introduced a collation mismatch between its explicit
 * utf8mb4_unicode_ci and the server default that only reproduces against a
 * real MySQL server; V39, whose {@code match_status VARCHAR(20)} silently
 * truncated the 21-character enum value {@code COUNTERPARTY_MISMATCH} on
 * real MySQL (strict SQL mode rejects the truncation outright) while H2
 * never exercised that code path in the fast unit suite — see
 * AbstractMySQLIntegrationTest).
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
    @DisplayName("flyway_schema_history: all 42 migrations recorded as successful, none pending")
    void allMigrationsAppliedSuccessfully() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<Boolean> successFlags = jdbc.queryForList(
                "SELECT success FROM flyway_schema_history ORDER BY installed_rank", Boolean.class);

        assertTrue(successFlags.size() >= 42,
                "Expected at least 42 applied migrations, found " + successFlags.size());
        assertFalse(successFlags.contains(false), "At least one migration is recorded as failed");

        Integer maxVersion = jdbc.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE version IS NOT NULL",
                Integer.class);
        assertEquals(42, maxVersion, "Highest applied migration version should be V42");
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

    @Test
    @DisplayName("electronic_invoices.match_status accepts the longest enum value (COUNTERPARTY_MISMATCH, 21 chars) without truncation")
    void matchStatusColumnAcceptsLongestEnumValue() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO organizations (id, name, slug) VALUES (9005, 'Match Status Length Test Org', 'match-status-length-test-org')");
        jdbc.update("INSERT INTO managers (id, first_name, last_name, email, organization_id) "
                + "VALUES (9005, 'Test', 'Manager', 'match-status-length-test@example.com', 9005)");
        jdbc.update("INSERT INTO counterparties (id, name, type, organization_id) "
                + "VALUES (9005, 'Test Customer', 'CUSTOMER', 9005)");
        jdbc.update("INSERT INTO contracts (id, counterparty_id, contract_number, manager_id, start_date, status, organization_id) "
                + "VALUES (9005, 9005, 'MATCH-STATUS-LENGTH-001', 9005, '2026-01-01', 'ACTIVE', 9005)");
        // The insert itself is the assertion: V39 sized this column VARCHAR(20),
        // one character too short for COUNTERPARTY_MISMATCH -- MySQL's strict
        // mode rejects the truncation with SQLState 22001 instead of silently
        // cutting the value short, which is exactly what surfaced this bug in
        // production (see V40).
        jdbc.update("INSERT INTO electronic_invoices "
                + "(id, contract_id, storage_path, file_name, file_size, content_type, match_status) "
                + "VALUES (9005, 9005, 'invoices/9005/9005/match-status-length-test.xml', 'test.xml', 1, 'application/xml', 'COUNTERPARTY_MISMATCH')");

        String matchStatus = jdbc.queryForObject(
                "SELECT match_status FROM electronic_invoices WHERE id = 9005", String.class);
        assertEquals("COUNTERPARTY_MISMATCH", matchStatus, "the enum value must round-trip untruncated");

        jdbc.update("DELETE FROM electronic_invoices WHERE id = 9005");
        jdbc.update("DELETE FROM contracts WHERE id = 9005");
        jdbc.update("DELETE FROM managers WHERE id = 9005");
        jdbc.update("DELETE FROM counterparties WHERE id = 9005");
        jdbc.update("DELETE FROM organizations WHERE id = 9005");
    }

    @Test
    @DisplayName("electronic_invoices: rejects a second invoice with the same org/supplier/number/type as a duplicate")
    void rejectsDuplicateInvoiceOnUniqueConstraint() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO organizations (id, name, slug) VALUES (9006, 'Invoice Dedupe Test Org', 'invoice-dedupe-test-org')");
        jdbc.update("INSERT INTO managers (id, first_name, last_name, email, organization_id) "
                + "VALUES (9006, 'Test', 'Manager', 'invoice-dedupe-test@example.com', 9006)");
        jdbc.update("INSERT INTO counterparties (id, name, type, organization_id) "
                + "VALUES (9006, 'Test Customer', 'CUSTOMER', 9006)");
        jdbc.update("INSERT INTO contracts (id, counterparty_id, contract_number, manager_id, start_date, status, organization_id) "
                + "VALUES (9006, 9006, 'INVOICE-DEDUPE-001', 9006, '2026-01-01', 'ACTIVE', 9006)");
        jdbc.update("INSERT INTO electronic_invoices "
                + "(id, contract_id, storage_path, file_name, file_size, content_type, org_id, supplier_vat_number, invoice_number, document_type) "
                + "VALUES (9006, 9006, 'invoices/9006/9006/dedupe-test-1.xml', 'test1.xml', 1, 'application/xml', 9006, 'IT12345678901', '2026/001', 'TD01')");

        DataAccessException duplicate = assertThrows(DataAccessException.class, () ->
                jdbc.update("INSERT INTO electronic_invoices "
                        + "(id, contract_id, storage_path, file_name, file_size, content_type, org_id, supplier_vat_number, invoice_number, document_type) "
                        + "VALUES (9007, 9006, 'invoices/9006/9006/dedupe-test-2.xml', 'test2.xml', 1, 'application/xml', 9006, 'IT12345678901', '2026/001', 'TD01')"));
        assertTrue(duplicate instanceof DataIntegrityViolationException,
                "a duplicate org_id/supplier_vat_number/invoice_number/document_type should violate the unique constraint");

        jdbc.update("DELETE FROM electronic_invoices WHERE id = 9006");
        jdbc.update("DELETE FROM contracts WHERE id = 9006");
        jdbc.update("DELETE FROM managers WHERE id = 9006");
        jdbc.update("DELETE FROM counterparties WHERE id = 9006");
        jdbc.update("DELETE FROM organizations WHERE id = 9006");
    }

    @Test
    @DisplayName("V42's JSON_TABLE extraction correctly explodes a FatturaPA-shaped line-items JSON array")
    void jsonTableExtractsLineItemsCorrectly() {
        // V42 dropped electronic_invoices.line_items_json after copying its data,
        // so this exercises the exact same JSON_TABLE clause the migration used
        // against a literal, Jackson-shaped JSON array instead of a pre-existing
        // row -- a fresh Testcontainers database has no pre-migration data for
        // the migration itself to have transformed by the time this test runs.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        String json = "[{\"lineNumber\":1,\"description\":\"Riga A\",\"quantity\":2.0000,"
                + "\"unitOfMeasure\":\"HUR\",\"unitPrice\":100.0000,\"totalPrice\":200.00,\"vatRate\":22.00},"
                + "{\"lineNumber\":2,\"description\":\"Riga B\",\"quantity\":1.0000,"
                + "\"unitOfMeasure\":\"NR\",\"unitPrice\":50.0000,\"totalPrice\":50.00,\"vatRate\":10.00}]";

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT jt.* FROM JSON_TABLE(?, '$[*]' COLUMNS ("
                        + "line_number INT PATH '$.lineNumber',"
                        + "description VARCHAR(1000) PATH '$.description',"
                        + "quantity DECIMAL(15,4) PATH '$.quantity',"
                        + "unit_of_measure VARCHAR(20) PATH '$.unitOfMeasure',"
                        + "unit_price DECIMAL(15,4) PATH '$.unitPrice',"
                        + "total_price DECIMAL(15,2) PATH '$.totalPrice',"
                        + "vat_rate DECIMAL(5,2) PATH '$.vatRate'"
                        + ")) AS jt",
                json);

        assertEquals(2, rows.size());
        assertEquals(1, ((Number) rows.get(0).get("line_number")).intValue());
        assertEquals("Riga A", rows.get(0).get("description"));
        assertEquals(0, new BigDecimal("200.00").compareTo((BigDecimal) rows.get(0).get("total_price")));
        assertEquals(2, ((Number) rows.get(1).get("line_number")).intValue());
        assertEquals("Riga B", rows.get(1).get("description"));
    }

    @Test
    @DisplayName("invoice_line_items: stores rows with a working FK cascade down from contracts through electronic_invoices")
    void invoiceLineItemsCascadesFromContractDeletion() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO organizations (id, name, slug) VALUES (9008, 'Line Item Migration Test Org', 'line-item-migration-test-org')");
        jdbc.update("INSERT INTO managers (id, first_name, last_name, email, organization_id) "
                + "VALUES (9008, 'Test', 'Manager', 'line-item-migration-test@example.com', 9008)");
        jdbc.update("INSERT INTO counterparties (id, name, type, organization_id) "
                + "VALUES (9008, 'Test Customer', 'CUSTOMER', 9008)");
        jdbc.update("INSERT INTO contracts (id, counterparty_id, contract_number, manager_id, start_date, status, organization_id) "
                + "VALUES (9008, 9008, 'LINE-ITEM-MIGRATION-001', 9008, '2026-01-01', 'ACTIVE', 9008)");
        jdbc.update("INSERT INTO electronic_invoices (id, contract_id, storage_path, file_name, file_size, content_type) "
                + "VALUES (9008, 9008, 'invoices/9008/9008/line-item-migration-test.xml', 'test.xml', 1, 'application/xml')");
        jdbc.update("INSERT INTO invoice_line_items "
                + "(invoice_id, line_number, description, quantity, unit_of_measure, unit_price, total_price, vat_rate) "
                + "VALUES (9008, 1, 'Riga di test', 1.0000, 'NR', 10.0000, 10.00, 22.00)");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoice_line_items WHERE invoice_id = 9008", Integer.class);
        assertEquals(1, count);

        jdbc.update("DELETE FROM contracts WHERE id = 9008");

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoice_line_items WHERE invoice_id = 9008", Integer.class);
        assertEquals(0, remaining, "invoice_line_items rows should cascade-delete through electronic_invoices with their contract");

        jdbc.update("DELETE FROM managers WHERE id = 9008");
        jdbc.update("DELETE FROM counterparties WHERE id = 9008");
        jdbc.update("DELETE FROM organizations WHERE id = 9008");
    }
}
