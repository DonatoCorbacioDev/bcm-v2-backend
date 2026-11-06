/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The decisions of the deletion job, with the database mocked. What the SQL
 * actually removes is checked against real MySQL in OrganizationPurgeIT.
 */
@DisplayName("Unit Test: OrganizationPurgeService")
class OrganizationPurgeServiceTest {

    private static final long ORG = 42L;

    private JdbcTemplate jdbc;
    private LocalStorageService storage;
    private IEmailService emailService;
    private AuditLogService auditLogService;
    private OrganizationPurgeService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        storage = mock(LocalStorageService.class);
        emailService = mock(IEmailService.class);
        auditLogService = mock(AuditLogService.class);
        service = new OrganizationPurgeService(jdbc, mock(PlatformTransactionManager.class), storage, emailService,
                auditLogService);
    }

    /** The row the job locks, with the given schedule and plan. */
    private void organization(LocalDateTime scheduledFor, String tier, String stripeCustomer) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "Acme S.r.l.");
        row.put("deletion_scheduled_for", scheduledFor == null ? null : Timestamp.valueOf(scheduledFor));
        row.put("subscription_tier", tier);
        row.put("stripe_customer_id", stripeCustomer);
        when(jdbc.queryForList(contains("FOR UPDATE"), eq(ORG))).thenReturn(List.of(row));
        when(jdbc.queryForList(contains("SELECT DISTINCT m.email"), eq(String.class), eq(ORG)))
                .thenReturn(List.of("admin@acme.test"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(ORG))).thenReturn(3);
    }

    private void due(Long... ids) {
        when(jdbc.queryForList(startsWith("SELECT id FROM organizations"), eq(Long.class), any(Timestamp.class)))
                .thenReturn(List.of(ids));
    }

    @Test
    @DisplayName("Deletes the rows in order, then the files, then emails the admins and writes a nameless audit row")
    void deletesDueOrganization() {
        due(ORG);
        organization(LocalDateTime.now().minusDays(1), "FREE", null);
        when(storage.deleteOrganizationFiles(ORG)).thenReturn(5);

        assertThat(service.purgeDueOrganizations()).isEqualTo(1);

        InOrder order = inOrder(jdbc, storage, emailService);
        for (String statement : OrganizationPurgeService.DELETE_STATEMENTS) {
            order.verify(jdbc).update(statement, ORG);
        }
        order.verify(storage).deleteOrganizationFiles(ORG);
        order.verify(emailService).sendOrganizationDeleted("admin@acme.test", "Acme S.r.l.");
        verify(auditLogService).save("ORGANIZATION_DELETED", "Organization", ORG, "system", ORG,
                "Organization deleted as scheduled: 3 contracts, 3 users");
    }

    @Test
    @DisplayName("The organizations row is the last one deleted")
    void organizationRowGoesLast() {
        List<String> statements = OrganizationPurgeService.DELETE_STATEMENTS;
        assertThat(statements.get(statements.size() - 1)).isEqualTo("DELETE FROM organizations WHERE id = ?");
        assertThat(statements.indexOf("DELETE FROM users WHERE organization_id = ?"))
                .isLessThan(statements.indexOf("DELETE FROM managers WHERE organization_id = ?"));
        assertThat(statements).noneMatch(s -> s.contains("terms_acceptances"));
    }

    @Test
    @DisplayName("Does nothing when no organization is due")
    void nothingDue() {
        due();

        assertThat(service.purgeDueOrganizations()).isZero();

        verify(jdbc, never()).update(anyString(), anyLong());
    }

    @Test
    @DisplayName("Skips an organization deleted or cancelled meanwhile, or not due any more")
    void skipsWhatChangedMeanwhile() {
        when(jdbc.queryForList(contains("FOR UPDATE"), eq(ORG))).thenReturn(List.of());
        assertThat(service.purge(ORG)).isFalse();

        organization(null, "FREE", null);
        assertThat(service.purge(ORG)).isFalse();

        organization(LocalDateTime.now().plusDays(1), "FREE", null);
        assertThat(service.purge(ORG)).isFalse();

        verify(jdbc, never()).update(anyString(), anyLong());
        verify(storage, never()).deleteOrganizationFiles(anyLong());
    }

    @Test
    @DisplayName("Skips an organization that has a Stripe-paid plan again, deletes one granted by hand")
    void paidPlan() {
        organization(LocalDateTime.now().minusDays(1), "PRO", "cus_123");
        assertThat(service.purge(ORG)).isFalse();
        verify(jdbc, never()).update(anyString(), anyLong());

        organization(LocalDateTime.now().minusDays(1), "PRO", null);
        assertThat(service.purge(ORG)).isTrue();
    }

    @Test
    @DisplayName("A failed organization doesn't stop the next one")
    void failureDoesNotStopTheOthers() {
        due(7L, ORG);
        when(jdbc.queryForList(contains("FOR UPDATE"), eq(7L))).thenThrow(new IllegalStateException("db down"));
        organization(LocalDateTime.now().minusDays(1), "FREE", null);

        assertThat(service.purgeDueOrganizations()).isEqualTo(1);

        verify(storage).deleteOrganizationFiles(ORG);
        verify(storage, never()).deleteOrganizationFiles(7L);
    }

    @Test
    @DisplayName("Files or emails failing after the commit still count the organization as deleted")
    void afterCommitFailuresAreLogged() {
        organization(LocalDateTime.now().minusDays(1), "FREE", null);
        when(storage.deleteOrganizationFiles(ORG)).thenThrow(new IllegalStateException("disk"));
        doThrow(new IllegalStateException("smtp")).when(emailService).sendOrganizationDeleted(anyString(), anyString());

        assertThat(service.purge(ORG)).isTrue();

        verify(emailService).sendOrganizationDeleted("admin@acme.test", "Acme S.r.l.");
    }

    @Test
    @DisplayName("Counts a skipped organization as not deleted, and a count the database returns empty as zero")
    void skippedAndEmptyCounts() {
        due(ORG);
        when(jdbc.queryForList(contains("FOR UPDATE"), eq(ORG))).thenReturn(List.of());
        assertThat(service.purgeDueOrganizations()).isZero();

        organization(LocalDateTime.now().minusDays(1), "FREE", null);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(ORG))).thenReturn(null);
        assertThat(service.purge(ORG)).isTrue();
        verify(auditLogService).save(anyString(), anyString(), eq(ORG), anyString(), eq(ORG),
                contains("0 contracts, 0 users"));
    }
}
