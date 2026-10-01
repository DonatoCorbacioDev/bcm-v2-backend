/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.entity.SepaPaymentBatch;
import com.donatodev.bcm_backend.repository.SepaPaymentBatchRepository;

/**
 * Purges SEPA payment batches (file on disk + DB row) older than the
 * configured retention window, closing the retention gap flagged in
 * {@code docs/GDPR.md} §5/§9: these files carry unmasked IBAN/BIC and
 * previously had no expiry at all, unlike audit logs.
 */
@Service
public class SepaPaymentRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(SepaPaymentRetentionService.class);

    private final SepaPaymentBatchRepository batchRepository;
    private final LocalStorageService localStorageService;

    @Value("${app.sepa-payment-retention-days:730}")
    private int retentionDays;

    public SepaPaymentRetentionService(SepaPaymentBatchRepository batchRepository,
                                        LocalStorageService localStorageService) {
        this.batchRepository = batchRepository;
        this.localStorageService = localStorageService;
    }

    /**
     * Deletes SEPA batches older than {@code retentionDays}. Runs every day
     * at 3:30 AM, after the audit log purge (3:00 AM). Invoices tagged with
     * a purged batch keep their own data (including {@code supplierIban});
     * only the generated payment file/record is removed (FK is {@code ON
     * DELETE SET NULL}, see V23).
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredSepaPayments() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<SepaPaymentBatch> expired = batchRepository.findByCreatedAtBefore(cutoff);
        for (SepaPaymentBatch batch : expired) {
            localStorageService.deleteDocument(batch.getStoragePath());
        }
        batchRepository.deleteAll(expired);
        logger.info("SEPA payment retention purge completed. {} batches older than {} days deleted.",
                expired.size(), retentionDays);
    }
}
