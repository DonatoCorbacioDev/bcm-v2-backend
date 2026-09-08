/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.repository.AuditLogRepository;

/**
 * Purges audit log entries older than the configured retention window,
 * closing the retention gap flagged in {@code docs/GDPR.md} §9.
 */
@Service
public class AuditLogRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogRetentionService.class);

    private final AuditLogRepository auditLogRepository;

    @Value("${app.audit-log-retention-days:180}")
    private int retentionDays;

    public AuditLogRetentionService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Deletes audit log entries older than {@code retentionDays}. Runs every
     * day at 3:00 AM, after the contract expiration checks (1:00/9:00 AM).
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredAuditLogs() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = auditLogRepository.deleteByTimestampBefore(cutoff);
        logger.info("Audit log retention purge completed. {} entries older than {} days deleted.",
                deleted, retentionDays);
    }
}
