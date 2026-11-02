package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;
import com.donatodev.bcm_backend.util.IbanValidator;

/**
 * Runs the public demo's nightly dataset (sql/DML/seed-demo-org.sql) against
 * real MySQL with the production prerequisites in place (org 3, manager 4,
 * the "demo" user, its business areas and financial types), and checks the
 * story the demo depends on: suppliers with VAT and a trusted IBAN, purchase
 * contracts with their monthly costs, a bank account for Nortiva itself.
 * Before this test the script only ever ran for real, at 04:15 in production.
 */
@SpringBootTest
@DisplayName("Integration Test: public demo dataset against real MySQL")
class SeedDemoOrgIT extends AbstractMySQLIntegrationTest {

    private static final Path SEED = Path.of("sql", "DML", "seed-demo-org.sql");
    private static final Path INVOICE_SCRIPT = Path.of("scripts", "seed_demo_purchase_invoices.py");

    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc;

    /** What production already has before the seed runs; the seed never creates it. */
    @BeforeEach
    void productionPrerequisites() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organizations (id, name, slug, subscription_tier) VALUES (3, 'BCM Demo', 'bcm-demo', 'FREE')"
                + " ON DUPLICATE KEY UPDATE name = name");
        jdbc.update("INSERT INTO managers (id, first_name, last_name, email, organization_id)"
                + " VALUES (4, 'Demo', 'Manager', 'demo-manager@seed.test', 3) ON DUPLICATE KEY UPDATE organization_id = 3");
        jdbc.update("INSERT INTO users (username, password_hash, manager_id, role_id, verified, organization_id)"
                + " SELECT 'demo', 'x', 4, (SELECT id FROM roles WHERE role = 'MANAGER'), 1, 3"
                + " WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'demo' AND organization_id = 3)");
        for (String area : List.of("Servizi IT", "Operazioni", "Legale")) {
            jdbc.update("INSERT INTO business_areas (name, description, organization_id) SELECT ?, '', 3"
                    + " WHERE NOT EXISTS (SELECT 1 FROM business_areas WHERE name = ? AND organization_id = 3)", area, area);
        }
        jdbc.update("INSERT INTO financial_types (name, category, organization_id) SELECT 'Ricavi', 'REVENUE', 3"
                + " WHERE NOT EXISTS (SELECT 1 FROM financial_types WHERE name = 'Ricavi' AND organization_id = 3)");
        jdbc.update("INSERT INTO financial_types (name, category, organization_id) SELECT 'Costi', 'COST', 3"
                + " WHERE NOT EXISTS (SELECT 1 FROM financial_types WHERE name = 'Costi' AND organization_id = 3)");
    }

    private void runSeed() throws IOException, SQLException {
        // The file selects the production database by name; the container's is bcm_it.
        String sql = Files.readString(SEED, StandardCharsets.UTF_8).replace("USE bcm;", "");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        }
    }

    private Map<String, Object> supplier(String name) {
        return jdbc.queryForMap("SELECT type, vat_number, verified_iban FROM counterparties"
                + " WHERE name = ? AND organization_id = 3", name);
    }

    @Test
    @DisplayName("Seeds ten contracts, two of them purchases from suppliers with VAT and a trusted IBAN")
    void seedsPurchaseSide() throws Exception {
        runSeed();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contracts WHERE organization_id = 3", Integer.class))
                .isEqualTo(10);
        for (String name : List.of("Nuvola Cloud Services S.r.l.", "Ferri & Associati S.r.l.")) {
            Map<String, Object> supplier = supplier(name);
            assertThat(supplier.get("type")).as(name).isEqualTo("SUPPLIER");
            assertThat((String) supplier.get("vat_number")).as(name).matches("IT\\d{11}");
            assertThat(IbanValidator.isValid((String) supplier.get("verified_iban"))).as(name).isTrue();
        }
        String nortivaIban = jdbc.queryForObject("SELECT iban FROM organizations WHERE id = 3", String.class);
        assertThat(IbanValidator.isValid(nortivaIban)).as("Nortiva's own IBAN, the SEPA debtor").isTrue();
    }

    @Test
    @DisplayName("Purchase contracts carry monthly/quarterly costs the invoices can match")
    void seedsCosts() throws Exception {
        runSeed();

        String costs = "SELECT COUNT(*) AS n, SUM(fv.financial_amount) AS total FROM financial_values fv"
                + " JOIN contracts c ON c.id = fv.contract_id JOIN financial_types t ON t.id = fv.financial_type_id"
                + " WHERE c.contract_number = ? AND c.organization_id = 3 AND t.name = 'Costi'";
        Map<String, Object> cloud = jdbc.queryForMap(costs, "NRT-2025-007");
        assertThat(((Number) cloud.get("n")).intValue()).isEqualTo(12);
        assertThat(((Number) cloud.get("total")).doubleValue()).isEqualTo(57600.0);
        Map<String, Object> advisory = jdbc.queryForMap(costs, "NRT-2025-008");
        assertThat(((Number) advisory.get("n")).intValue()).isEqualTo(4);
        // Both cost lines include the current month: the nightly invoices are dated today's month.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_values fv JOIN contracts c ON c.id = fv.contract_id"
                + " WHERE c.contract_number IN ('NRT-2025-007', 'NRT-2025-008') AND fv.month_value = MONTH(CURDATE())"
                + " AND fv.year_value = YEAR(CURDATE())", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("Running it again, as every night, duplicates nothing")
    void isIdempotent() throws Exception {
        runSeed();
        runSeed();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contracts WHERE organization_id = 3", Integer.class))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM counterparties WHERE organization_id = 3 AND type = 'SUPPLIER'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("The nightly invoice script uses the same supplier VAT numbers and trusted IBAN")
    void invoiceScriptMatchesSeed() throws Exception {
        runSeed();
        String script = Files.readString(INVOICE_SCRIPT, StandardCharsets.UTF_8);

        // Matching compares VAT numbers, SEPA the trusted IBAN: a typo in
        // either file would quietly turn the demo red again.
        for (String name : List.of("Nuvola Cloud Services S.r.l.", "Ferri & Associati S.r.l.")) {
            assertThat(script).as(name).contains((String) supplier(name).get("vat_number"));
        }
        assertThat(script).contains((String) supplier("Nuvola Cloud Services S.r.l.").get("verified_iban"));
        // Ferri's invoice must NOT carry the trusted IBAN: it is the anti-fraud scenario.
        assertThat(script).doesNotContain((String) supplier("Ferri & Associati S.r.l.").get("verified_iban"));
    }
}
