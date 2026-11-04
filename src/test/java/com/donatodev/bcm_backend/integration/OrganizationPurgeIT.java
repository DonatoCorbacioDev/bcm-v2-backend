/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.donatodev.bcm_backend.service.IEmailService;
import com.donatodev.bcm_backend.service.LocalStorageService;
import com.donatodev.bcm_backend.service.OrganizationPurgeService;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * The deletion job against real MySQL, real foreign keys and real files: two
 * organizations get a row in every table and a file in every storage root,
 * one is deleted, and the test checks that nothing of it is left (also in
 * tables with no organization column) while the other is untouched.
 * <p>
 * The "nothing left" check reads the schema instead of a fixed list, so a
 * future table with an organization column fails here until the job learns
 * to delete it.
 */
@SpringBootTest
@DisplayName("Integration Test: scheduled organization deletion against real MySQL")
class OrganizationPurgeIT extends AbstractMySQLIntegrationTest {

    /** Kept after the deletion on purpose (see OrganizationPurgeService). */
    private static final List<String> KEPT = List.of("terms_acceptances", "audit_logs");

    @Autowired private DataSource dataSource;
    @Autowired private OrganizationPurgeService purgeService;
    @Autowired private LocalStorageService storage;
    @MockitoBean private IEmailService emailService;

    private JdbcTemplate jdbc;

    /** Ids of everything seeded for one organization, by table. */
    private record Seeded(long orgId, String name, String adminEmail, Map<String, Long> ids, List<String> files) {}

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("Deletes every row and file of a due organization and nothing of another one")
    void deletesOnlyTheDueOrganization() {
        Seeded doomed = seed("Doomed");
        Seeded other = seed("Other");
        schedule(doomed.orgId(), "NOW() - INTERVAL 1 DAY");
        Map<String, Integer> otherBefore = rowsOf(other);

        int deleted = purgeService.purgeDueOrganizations();

        assertThat(deleted).isEqualTo(1);
        // Every table holding an organization id, read from the schema.
        for (String[] column : organizationColumns()) {
            if (KEPT.contains(column[0])) {
                continue;
            }
            assertThat(countWhere(column[0], column[1], doomed.orgId())).as(column[0]).isZero();
        }
        // Tables reached only through a user, manager, contract or invoice.
        rowsOf(doomed).forEach((table, n) -> assertThat(n).as(table).isZero());
        assertThat(rowsOf(other)).isEqualTo(otherBefore);
        assertThat(countWhere("organizations", "id", other.orgId())).isOne();

        // What stays: the proof of the Terms, and one audit row without names.
        assertThat(countWhere("terms_acceptances", "organization_id", doomed.orgId())).isOne();
        List<Map<String, Object>> audit = jdbc.queryForList(
                "SELECT action, username, details FROM audit_logs WHERE org_id = ?", doomed.orgId());
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0)).containsEntry("action", "ORGANIZATION_DELETED")
                .containsEntry("username", "system");
        assertThat((String) audit.get(0).get("details")).doesNotContain(doomed.name());

        for (String path : doomed.files()) {
            assertThatThrownBy(() -> storage.readDocument(path)).as(path).isInstanceOf(RuntimeException.class);
        }
        for (String path : other.files()) {
            assertThat(storage.readDocument(path)).as(path).isNotEmpty();
        }
        verify(emailService).sendOrganizationDeleted(doomed.adminEmail(), doomed.name());
        verify(emailService, never()).sendOrganizationDeleted(eq(other.adminEmail()), anyString());
    }

    @Test
    @DisplayName("Leaves alone a deletion not due yet, a cancelled one and one with a paid plan")
    void skipsWhatIsNotDue() {
        Seeded later = seed("Later");
        Seeded cancelled = seed("Cancelled");
        Seeded paying = seed("Paying");
        schedule(later.orgId(), "NOW() + INTERVAL 1 DAY");
        schedule(paying.orgId(), "NOW() - INTERVAL 1 DAY");
        jdbc.update("UPDATE organizations SET subscription_tier = 'PRO', stripe_customer_id = 'cus_test' WHERE id = ?",
                paying.orgId());

        purgeService.purgeDueOrganizations();

        for (Seeded org : List.of(later, cancelled, paying)) {
            assertThat(countWhere("organizations", "id", org.orgId())).as(org.name()).isOne();
            rowsOf(org).forEach((table, n) -> assertThat(n).as(org.name() + " " + table).isOne());
        }
    }

    @Test
    @DisplayName("A failure rolls the whole organization back, files included, and is retried later")
    void rollsBackOnFailure() {
        Seeded doomed = seed("Broken");
        Seeded other = seed("Neighbour");
        // A row of another organization pointing at this one's manager (no
        // cascade on contract_manager.manager_id): deleting the managers fails.
        jdbc.update("INSERT INTO contract_manager (contract_id, manager_id) VALUES (?, ?)",
                other.ids().get("contracts"), doomed.ids().get("managers"));
        schedule(doomed.orgId(), "NOW() - INTERVAL 1 DAY");

        int deleted = purgeService.purgeDueOrganizations();

        assertThat(deleted).isZero();
        rowsOf(doomed).forEach((table, n) -> assertThat(n).as(table).isOne());
        assertThat(jdbc.queryForObject("SELECT deletion_scheduled_for FROM organizations WHERE id = ?",
                Object.class, doomed.orgId())).as("still scheduled").isNotNull();
        for (String path : doomed.files()) {
            assertThat(storage.readDocument(path)).as(path).isNotEmpty();
        }
        verify(emailService, never()).sendOrganizationDeleted(anyString(), anyString());
    }

    // ── seeding ──────────────────────────────────────────────────────────────

    /** One row in every table an organization can reach, one file per storage root. */
    private Seeded seed(String label) {
        String tag = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        String name = "Org " + tag;
        String email = tag.toLowerCase() + "@purge.test";
        Map<String, Long> ids = new LinkedHashMap<>();
        long org = insert(ids, "organizations", "INSERT INTO organizations (name, slug, subscription_tier) VALUES (?, ?, 'FREE')",
                name, tag.toLowerCase());
        long area = insert(ids, "business_areas", "INSERT INTO business_areas (name, organization_id) VALUES (?, ?)", "Area " + tag, org);
        long type = insert(ids, "financial_types",
                "INSERT INTO financial_types (name, category, organization_id) VALUES (?, 'REVENUE', ?)", "Ricavi " + tag, org);
        long manager = insert(ids, "managers",
                "INSERT INTO managers (first_name, last_name, email, organization_id) VALUES ('Ada', 'Admin', ?, ?)", email, org);
        long user = insert(ids, "users", "INSERT INTO users (username, password_hash, manager_id, role_id, organization_id)"
                + " VALUES (?, 'x', ?, (SELECT id FROM roles WHERE role = 'ADMIN'), ?)", "admin-" + tag, manager, org);
        long counterparty = insert(ids, "counterparties",
                "INSERT INTO counterparties (name, type, organization_id) VALUES (?, 'CUSTOMER', ?)", "Cliente " + tag, org);
        long contract = insert(ids, "contracts", "INSERT INTO contracts (contract_number, counterparty_id, organization_id, start_date,"
                + " status, area_id, financial_type_id, manager_id) VALUES (?, ?, ?, CURDATE(), 'ACTIVE', ?, ?, ?)",
                "C-" + tag, counterparty, org, area, type, manager);
        insert(ids, "contract_manager", "INSERT INTO contract_manager (contract_id, manager_id) VALUES (?, ?)", contract, manager);
        insert(ids, "contract_history", "INSERT INTO contract_history (contract_id, modified_by) VALUES (?, ?)", contract, user);
        insert(ids, "contract_workflow_events", "INSERT INTO contract_workflow_events (contract_id, actor_user_id, action, to_stage)"
                + " VALUES (?, ?, 'SUBMIT', 'IN_REVIEW')", contract, user);

        String document = storage.storeDocument(org, contract, bytes(tag));
        String invoiceFile = storage.storeInvoice(org, contract, bytes(tag));
        String sepaFile = storage.storeSepaPayment(org, contract, bytes(tag));
        insert(ids, "contract_documents", "INSERT INTO contract_documents (contract_id, org_id, file_name, file_size, storage_path,"
                + " version_number) VALUES (?, ?, 'a.pdf', 1, ?, 1)", contract, org, document);
        long value = insert(ids, "financial_values", "INSERT INTO financial_values (financial_amount, month_value, year_value,"
                + " organization_id, contract_id, financial_type_id, area_id) VALUES (100, 1, 2026, ?, ?, ?, ?)",
                org, contract, type, area);
        long batch = insert(ids, "sepa_payment_batches", "INSERT INTO sepa_payment_batches (contract_id, org_id, currency,"
                + " execution_date, file_name, message_id, number_of_transactions, storage_path, total_amount)"
                + " VALUES (?, ?, 'EUR', CURDATE(), 's.xml', ?, 1, ?, 100)", contract, org, "M-" + tag, sepaFile);
        long invoice = insert(ids, "electronic_invoices", "INSERT INTO electronic_invoices (contract_id, org_id, file_name, file_size,"
                + " storage_path, matched_financial_value_id, sepa_batch_id, matched_by_user_id) VALUES (?, ?, 'i.xml', 1, ?, ?, ?, ?)",
                contract, org, invoiceFile, value, batch, user);
        insert(ids, "invoice_line_items", "INSERT INTO invoice_line_items (invoice_id) VALUES (?)", invoice);
        insert(ids, "risk_feedback", "INSERT INTO risk_feedback (agree, contract_id, org_id, risk_level, risk_score, submitted_by)"
                + " VALUES (1, ?, ?, 'HIGH', 0.9, ?)", contract, org, user);
        insert(ids, "notifications", "INSERT INTO notifications (message, org_id, title, user_id) VALUES ('m', ?, 't', ?)", org, user);
        insert(ids, "ml_result_cache", "INSERT INTO ml_result_cache (cache_key, computed_at, json_result, org_id)"
                + " VALUES (?, NOW(), '{}', ?)", "k-" + tag, org);
        insert(ids, "audit_logs", "INSERT INTO audit_logs (action, entity_type, org_id, username) VALUES ('LOGIN', 'User', ?, ?)",
                org, "admin-" + tag);
        insert(ids, "budgets", "INSERT INTO budgets (business_area_id, category, organization_id, target_amount, year_value)"
                + " VALUES (?, 'REVENUE', ?, 1000, 2026)", area, org);
        insert(ids, "contract_templates", "INSERT INTO contract_templates (name, org_id, business_area_id, default_manager_id)"
                + " VALUES ('T', ?, ?, ?)", org, area, manager);
        insert(ids, "invite_token", "INSERT INTO invite_token (expiry_date, manager_id, role, token, username)"
                + " VALUES (NOW(), ?, 'MANAGER', ?, ?)", manager, "inv-" + tag, "invited-" + tag);
        insert(ids, "password_reset_token", "INSERT INTO password_reset_token (expiry_date, token, user_id) VALUES (NOW(), ?, ?)",
                "pwd-" + tag, user);
        insert(ids, "refresh_tokens", "INSERT INTO refresh_tokens (expiry_date, token, user_id) VALUES (NOW(), ?, ?)",
                "ref-" + tag, user);
        insert(ids, "verification_token", "INSERT INTO verification_token (expiry_date, token, user_id) VALUES (NOW(), ?, ?)",
                "ver-" + tag, user);
        insert(ids, "totp_recovery_codes", "INSERT INTO totp_recovery_codes (code_hash, user_id) VALUES ('h', ?)", user);
        insert(ids, "organization_deletion_codes", "INSERT INTO organization_deletion_codes (code_hash, code_sent_at, expires_at,"
                + " organization_id, user_id) VALUES ('h', NOW(), NOW(), ?, ?)", org, user);
        insert(ids, "terms_acceptances", "INSERT INTO terms_acceptances (accepted_at, code_sent_at, onerous_clauses, organization_id,"
                + " organization_name, professional_use_declared, signature_method, signer_email, terms_version, user_id, username)"
                + " VALUES (NOW(), NOW(), '1,2', ?, ?, 1, 'OTP_EMAIL', ?, 'v1', ?, ?)", org, name, email, user, "admin-" + tag);
        return new Seeded(org, name, email, ids, List.of(document, invoiceFile, sepaFile));
    }

    private long insert(Map<String, Long> ids, String table, String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, key);
        // contract_manager has a composite key and returns none.
        long id = key.getKeyList().isEmpty() ? 0L : Objects.requireNonNull(key.getKey()).longValue();
        ids.put(table, id);
        return id;
    }

    private static byte[] bytes(String tag) {
        return tag.getBytes(StandardCharsets.UTF_8);
    }

    private void schedule(long orgId, String when) {
        jdbc.update("UPDATE organizations SET deletion_scheduled_for = " + when + ", deletion_requested_at = NOW(),"
                + " deletion_requested_by = 'admin' WHERE id = ?", orgId);
    }

    // ── checks ───────────────────────────────────────────────────────────────

    /** How many of the rows seeded for this organization are still there, by table. */
    private Map<String, Integer> rowsOf(Seeded org) {
        Map<String, Integer> rows = new LinkedHashMap<>();
        org.ids().forEach((table, id) -> {
            if (KEPT.contains(table)) {
                return;
            }
            rows.put(table, table.equals("contract_manager")
                    ? countWhere(table, "contract_id", org.ids().get("contracts"))
                    : countWhere(table, "id", id));
        });
        return rows;
    }

    private List<String[]> organizationColumns() {
        return jdbc.query("SELECT table_name, column_name FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND column_name IN ('organization_id', 'org_id')",
                (rs, n) -> new String[] {rs.getString(1), rs.getString(2)});
    }

    private int countWhere(String table, String column, long value) {
        // Table and column come from this class or from information_schema, never from input.
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
        return n == null ? 0 : n;
    }
}
