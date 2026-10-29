/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.donatodev.bcm_backend.entity.SepaPaymentBatch;

/**
 * One element of {@code data/sepa_payment_batches.json}; the generated XML is
 * at {@code archivePath}. The storage path is internal.
 */
public record SepaPaymentBatchExportDTO(
        Long id,
        Long contractId,
        String archivePath,
        String messageId,
        LocalDate executionDate,
        BigDecimal totalAmount,
        String currency,
        Integer numberOfTransactions,
        String fileName,
        Instant createdAt) {

    public static SepaPaymentBatchExportDTO from(SepaPaymentBatch batch) {
        return new SepaPaymentBatchExportDTO(
                batch.getId(),
                batch.getContract().getId(),
                ExportFilePaths.of(batch),
                batch.getMessageId(),
                batch.getExecutionDate(),
                batch.getTotalAmount(),
                batch.getCurrency(),
                batch.getNumberOfTransactions(),
                batch.getFileName(),
                batch.getCreatedAt());
    }
}
