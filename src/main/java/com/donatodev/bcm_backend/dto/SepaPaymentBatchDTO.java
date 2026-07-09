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
