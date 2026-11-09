/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.UUID;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.donatodev.bcm_backend.entity.SubscriptionTier;

/**
 * Carries out the deletions scheduled by {@link OrganizationDeletionService}
 * once their day has come: every row of the organization, then its files,
 * then an email to whoever was an admin.
 * <p>
 * Each organization is deleted in its own transaction, so a failure rolls
 * that one back in full, leaves it scheduled for the next night and doesn't
 * stop the others. Files are removed only after the commit: a rollback must
 * never leave rows pointing at files that are gone.
 * <p>
 * Kept on purpose: {@code terms_acceptances} (proof of the contract, 10 years,
 * see V47; no foreign key, so nothing cascades into it) and one
 * {@code ORGANIZATION_DELETED} audit row that holds only ids and counts.
 */
@Service
public class OrganizationPurgeService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationPurgeService.class);

    static final String AUDIT_ACTION = "ORGANIZATION_DELETED";
    static final String AUDIT_USER = "system";

    /**
     * Children before parents, every statement scoped to the organization.
     * Deleting a contract cascades (V11-V42) to its documents, history,
     * manager links, workflow events, invoices and their lines, financial
     * values, risk feedback and SEPA batches; the explicit statements after
     * it catch any row of those tables not tied to one of its contracts.
     * Users cascade to their tokens, 2FA recovery codes and notifications,
     * and go before managers (users.manager_id has no cascade).
     * A new table with an organization column must be added here: the
     * integration test fails until it is.
     */
    static final List<String> DELETE_STATEMENTS = List.of(
            "DELETE FROM contracts WHERE organization_id = ?",
            "DELETE FROM financial_values WHERE organization_id = ?",
            "DELETE FROM electronic_invoices WHERE org_id = ?",
            "DELETE FROM contract_documents WHERE org_id = ?",
            "DELETE FROM sepa_payment_batches WHERE org_id = ?",
            "DELETE FROM risk_feedback WHERE org_id = ?",
            "DELETE FROM notifications WHERE org_id = ?",
            "DELETE FROM ml_result_cache WHERE org_id = ?",
            "DELETE FROM audit_logs WHERE org_id = ?",
            "DELETE FROM organization_deletion_codes WHERE organization_id = ?",
            "DELETE FROM users WHERE organization_id = ?",
            "DELETE FROM contract_templates WHERE org_id = ?",
            "DELETE FROM managers WHERE organization_id = ?",
            "DELETE FROM budgets WHERE organization_id = ?",
            "DELETE FROM counterparties WHERE organization_id = ?",
            "DELETE FROM business_areas WHERE organization_id = ?",
            "DELETE FROM financial_types WHERE organization_id = ?",
            "DELETE FROM organizations WHERE id = ?");

    /** What has to be known about an organization before its rows are gone. */
    record DeletedOrganization(UUID id, Long legacyId, String name, List<String> adminEmails, int contracts, int users) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final LocalStorageService storage;
    private final IEmailService emailService;
    private final AuditLogService auditLogService;

    public OrganizationPurgeService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            LocalStorageService storage, IEmailService emailService, AuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.storage = storage;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    /**
     * Runs every night at 2:00, before the audit log purge (3:00) and the
     * backups, so the next backup no longer contains the deleted data.
     *
     * @return how many organizations were deleted
     */
    @Scheduled(cron = "0 0 2 * * *")
    public int purgeDueOrganizations() {
        // Ordered by when deletion was scheduled: ids carry no order (ADR-0009).
        List<UUID> due = jdbc.queryForList(
                "SELECT id FROM organizations WHERE deletion_scheduled_for <= ? ORDER BY deletion_scheduled_for, id",
                String.class, Timestamp.valueOf(now())).stream().map(UUID::fromString).toList();
        int deleted = 0;
        for (UUID orgId : due) {
            try {
                if (purge(orgId)) {
                    deleted++;
                }
            } catch (RuntimeException e) {
                // Rolled back: still scheduled, retried tomorrow night.
                logger.error("Deletion of organization {} failed, will retry: {}", orgId, e.getClass().getSimpleName(), e);
            }
        }
        if (!due.isEmpty()) {
            logger.info("Organization purge completed: {} of {} due organizations deleted.", deleted, due.size());
        }
        return deleted;
    }

    /**
     * Deletes one organization if its deletion is still scheduled and due.
     *
     * @return false if it was skipped (cancelled meanwhile, or a paid plan)
     */
    boolean purge(UUID orgId) {
        Optional<DeletedOrganization> result = transaction.execute(status -> deleteRows(orgId));
        if (result.isEmpty()) {
            return false;
        }
        DeletedOrganization deleted = result.get();

        int files = 0;
        try {
            files = storage.deleteOrganizationFiles(orgId, deleted.legacyId());
        } catch (RuntimeException e) {
            // The rows are gone, so nothing points at these files any more:
            // they need removing by hand, but must not block the emails.
            logger.error("Organization {} deleted, but its files could not be removed: {}",
                    orgId, e.getClass().getSimpleName(), e);
        }
        for (String email : deleted.adminEmails()) {
            try {
                emailService.sendOrganizationDeleted(email, deleted.name());
            } catch (RuntimeException e) {
                logger.warn("Organization {} deleted, but a confirmation email failed: {}",
                        orgId, e.getClass().getSimpleName());
            }
        }
        logger.info("Organization {} deleted: {} contracts, {} users, {} files.",
                orgId, deleted.contracts(), deleted.users(), files);
        return true;
    }

    /** Empty when the organization is skipped: gone, no longer due, or on a paid plan again. */
    private Optional<DeletedOrganization> deleteRows(UUID orgId) {
        // CHAR(36) column: the driver needs the text form, not a java.util.UUID.
        String id = orgId.toString();
        // Locked: a "cancel" arriving now waits, then finds nothing to cancel.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT name, deletion_scheduled_for, subscription_tier, stripe_customer_id, legacy_id"
                + " FROM organizations WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> org = rows.get(0);
        Timestamp scheduledFor = (Timestamp) org.get("deletion_scheduled_for");
        if (scheduledFor == null || scheduledFor.toLocalDateTime().isAfter(now())) {
            return Optional.empty();
        }
        // Same rule as at confirmation: a live Stripe subscription would keep
        // billing an organization that no longer exists.
        if (!SubscriptionTier.FREE.name().equals(org.get("subscription_tier")) && org.get("stripe_customer_id") != null) {
            logger.warn("Organization {} not deleted: it has a paid subscription again.", orgId);
            return Optional.empty();
        }

        List<String> adminEmails = jdbc.queryForList(
                "SELECT DISTINCT m.email FROM users u JOIN roles r ON r.id = u.role_id"
                + " JOIN managers m ON m.id = u.manager_id"
                + " WHERE u.organization_id = ? AND r.role = 'ADMIN'", String.class, id);
        int contracts = count("SELECT COUNT(*) FROM contracts WHERE organization_id = ?", id);
        int users = count("SELECT COUNT(*) FROM users WHERE organization_id = ?", id);

        for (String statement : DELETE_STATEMENTS) {
            jdbc.update(statement, id);
        }
        // Ids and counts only: the name and the people are what was asked to be forgotten.
        auditLogService.save(AUDIT_ACTION, "Organization", orgId, AUDIT_USER, orgId,
                "Organization deleted as scheduled: " + contracts + " contracts, " + users + " users");
        Long legacyId = org.get("legacy_id") instanceof Number n ? n.longValue() : null;
        return Optional.of(new DeletedOrganization(orgId, legacyId, (String) org.get("name"), adminEmails, contracts, users));
    }

    private int count(String sql, String orgId) {
        Integer n = jdbc.queryForObject(sql, Integer.class, orgId);
        return n == null ? 0 : n;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }
}
