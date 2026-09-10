/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

/**
 * Org-wide (or, for a MANAGER, own-contracts-only) invoicing rollup for a
 * given year: expected value from {@code FinancialValues} vs. what has
 * actually been confirmed-invoiced. Only {@code CONFIRMED} matches count as
 * invoiced, same rule as {@link CounterpartyInvoicingSummaryDTO}.
 */
public record OrganizationInvoicingSummaryDTO(
        int year,
        double expectedYtd,
        double invoicedYtd,
        double variance,
        double variancePercent) {
}
