/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;

/**
 * Invoicing rollup for a single counterparty: contracted value vs. what has
 * actually been confirmed-invoiced this year. Only {@code CONFIRMED} matches
 * count as invoiced -- a {@code SUGGESTED} match a human hasn't confirmed yet
 * doesn't count, consistent with the matching feature never auto-confirming.
 */
public record CounterpartyInvoicingSummaryDTO(
        Long counterpartyId,
        String counterpartyName,
        long activeContracts,
        double contractedValue,
        double invoicedYtd,
        double variancePercent,
        long invoiceCount,
        LocalDate lastInvoiceDate) {
}
