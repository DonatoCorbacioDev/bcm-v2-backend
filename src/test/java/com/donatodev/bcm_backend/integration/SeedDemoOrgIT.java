package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
    @DisplayName("Seeds thirteen contracts, the invoiced purchases from suppliers with VAT and a trusted IBAN")
    void seedsPurchaseSide() throws Exception {
        runSeed();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contracts WHERE organization_id = 3", Integer.class))
                .isEqualTo(13);
        for (String name : List.of("Nuvola Cloud Services S.r.l.", "Ferri & Associati S.r.l.")) {
            Map<String, Object> supplier = supplier(name);
            assertThat(supplier).as(name).containsEntry("type", "SUPPLIER");
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
        Map<String, Object> cloud = jdbc.queryForMap(costs, "NRT-117");
        assertThat(((Number) cloud.get("n")).intValue()).isEqualTo(12);
        assertThat(((Number) cloud.get("total")).doubleValue()).isEqualTo(57600.0);
        Map<String, Object> advisory = jdbc.queryForMap(costs, "NRT-118");
        assertThat(((Number) advisory.get("n")).intValue()).isEqualTo(4);
        // Both cost lines include the current month: the nightly invoices are dated today's month.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_values fv JOIN contracts c ON c.id = fv.contract_id"
                + " WHERE c.contract_number IN ('NRT-117', 'NRT-118') AND fv.month_value = MONTH(CURDATE())"
                + " AND fv.year_value = YEAR(CURDATE())", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("Budgets are sized from this year's values, so each area shows the same pace whatever the date")
    void seedsBudgetsFromYearToDateValues() throws Exception {
        runSeed();

        double monthsElapsedShare = jdbc.queryForObject("SELECT MONTH(CURDATE()) / 12", Double.class);
        Map<String, Double> factors = Map.of(
                "REVENUE:Servizi IT", 0.92, "REVENUE:Finanza", 1.00,
                "REVENUE:Operazioni", 1.12, "REVENUE:Legale", 1.50,
                "COST:Servizi IT", 1.20, "COST:Finanza", 1.25);
        List<Map<String, Object>> budgets = jdbc.queryForList("SELECT CONCAT(b.category, ':', ba.name) AS area,"
                + " b.target_amount AS target,"
                + " (SELECT COALESCE(SUM(fv.financial_amount), 0) FROM financial_values fv"
                + "    JOIN financial_types ft ON ft.id = fv.financial_type_id"
                + "    WHERE fv.organization_id = 3 AND fv.area_id = b.business_area_id"
                + "      AND ft.category = b.category AND fv.year_value = b.year_value) AS actual"
                + " FROM budgets b JOIN business_areas ba ON ba.id = b.business_area_id"
                + " WHERE b.organization_id = 3 AND b.year_value = YEAR(CURDATE())");

        // Both have a value in the current month, so they always have a budget.
        assertThat(budgets).extracting(b -> b.get("area")).contains("REVENUE:Servizi IT", "REVENUE:Operazioni");
        // No cost is ever booked on these two: no empty cost target.
        assertThat(budgets).extracting(b -> b.get("area")).doesNotContain("COST:Operazioni", "COST:Legale");
        for (Map<String, Object> budget : budgets) {
            String area = (String) budget.get("area");
            double actual = ((Number) budget.get("actual")).doubleValue();
            double target = ((Number) budget.get("target")).doubleValue();
            assertThat(actual).as(area).isPositive();
            // Targets are rounded to the thousand, hence the small tolerance.
            assertThat(actual / target).as(area)
                    .isCloseTo(monthsElapsedShare / factors.get(area), within(0.03));
        }
    }

    @Test
    @DisplayName("Seeds a tacitly renewing maintenance contract whose last day to give notice is in 20 days")
    void seedsTacitRenewal() throws Exception {
        runSeed();

        Map<String, Object> maintenance = jdbc.queryForMap(
                "SELECT auto_renew, notice_days, renewal_months, DATEDIFF(DATE_SUB(end_date, INTERVAL notice_days DAY), CURDATE()) AS days_to_notice"
                        + " FROM contracts WHERE contract_number = 'NRT-119' AND organization_id = 3");
        assertThat(maintenance).containsEntry("auto_renew", true);
        assertThat(((Number) maintenance.get("notice_days")).intValue()).isEqualTo(90);
        assertThat(((Number) maintenance.get("renewal_months")).intValue()).isEqualTo(12);
        assertThat(((Number) maintenance.get("days_to_notice")).intValue()).isEqualTo(20);
        assertThat(supplier("Rossi Impianti S.r.l.")).containsEntry("type", "SUPPLIER");
    }

    @Test
    @DisplayName("Three suppliers renew tacitly, with notice due in 6, 20 and 45 days")
    void seedsTacitRenewals() throws Exception {
        runSeed();

        List<Integer> daysToNotice = jdbc.queryForList(
                "SELECT DATEDIFF(DATE_SUB(c.end_date, INTERVAL c.notice_days DAY), CURDATE()) FROM contracts c"
                        + " JOIN counterparties cp ON cp.id = c.counterparty_id"
                        + " WHERE c.organization_id = 3 AND c.auto_renew AND cp.type = 'SUPPLIER' ORDER BY 1", Integer.class);
        assertThat(daysToNotice).containsExactly(6, 20, 45);
    }

    @Test
    @DisplayName("Every counterparty has a contact person, and every tacitly renewing purchase a yearly value")
    void seedsContactsAndRenewalValues() throws Exception {
        runSeed();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM counterparties WHERE organization_id = 3"
                + " AND (contact_name IS NULL OR contact_name = '')", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT c.annual_value FROM contracts c JOIN counterparties cp ON cp.id = c.counterparty_id"
                + " WHERE c.organization_id = 3 AND c.auto_renew AND cp.type = 'SUPPLIER' ORDER BY c.contract_number", Double.class))
                .containsExactly(6000.0, 2400.0, 9600.0);
    }

    @Test
    @DisplayName("Every counterparty has a city, except the two suppliers whose address comes from their invoices")
    void seedsCities() throws Exception {
        runSeed();

        assertThat(jdbc.queryForList("SELECT name FROM counterparties WHERE organization_id = 3"
                + " AND (city IS NULL OR city = '') ORDER BY name", String.class))
                .containsExactly("Ferri & Associati S.r.l.", "Nuvola Cloud Services S.r.l.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM counterparties WHERE organization_id = 3"
                + " AND city IS NOT NULL AND (province IS NULL OR CHAR_LENGTH(province) <> 2)", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Contract numbers carry no year, so they never contradict dates that move every night")
    void contractNumbersCarryNoYear() throws Exception {
        runSeed();

        assertThat(jdbc.queryForList("SELECT contract_number FROM contracts WHERE organization_id = 3", String.class))
                .allMatch(number -> number.matches("NRT-1\\d{2}"));
    }

    @Test
    @DisplayName("Wipes the demo's notifications, which would otherwise outlive the contracts they mention")
    void wipesNotifications() throws Exception {
        Long demoUser = jdbc.queryForObject("SELECT id FROM users WHERE username = 'demo' AND organization_id = 3", Long.class);
        jdbc.update("INSERT INTO notifications (user_id, org_id, title, message) VALUES (?, 3, 'Rischio', 'SYN-03-0028')", demoUser);

        runSeed();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE org_id = 3", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Running it again, as every night, duplicates nothing")
    void isIdempotent() throws Exception {
        runSeed();
        runSeed();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contracts WHERE organization_id = 3", Integer.class))
                .isEqualTo(13);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM counterparties WHERE organization_id = 3 AND type = 'SUPPLIER'",
                Integer.class)).isEqualTo(6);
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
        // Past invoices are paid on each supplier's trusted IBAN; Ferri's
        // newest one carries a different IBAN: the anti-fraud scenario.
        for (String name : List.of("Nuvola Cloud Services S.r.l.", "Ferri & Associati S.r.l.")) {
            assertThat(script).as(name).contains((String) supplier(name).get("verified_iban"));
        }
        assertThat(script).contains("FERRI_NEW_IBAN = \"IT07B3600003200000087345519\"")
                .doesNotContain("FERRI_NEW_IBAN = \"" + supplier("Ferri & Associati S.r.l.").get("verified_iban") + "\"");
    }
}
