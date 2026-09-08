/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SepaPaymentBatchDTO(
        Long id,
        Long contractId,
        LocalDate executionDate,
        BigDecimal totalAmount,
        String currency,
        Integer numberOfTransactions,
        String fileName,
        Instant createdAt
) {}
