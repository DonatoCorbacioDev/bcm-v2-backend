/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialValueSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.donatodev.bcm_backend.validation.InputLimits;

/**
 * Data Transfer Object for Financial Values.
 * <p>
 * Represents a monetary value associated with a specific financial type,
 * business area, and contract for a given month and year.
 *
 * @param id the unique identifier of the financial value
 * @param month the month (1-12) to which the value refers
 * @param year the year to which the value refers
 * @param financialAmount the financial amount (e.g., revenue or cost)
 * @param financialTypeId the ID of the financial type (e.g., Revenue, Cost)
 * @param businessAreaId the ID of the related business area
 * @param contractId the ID of the associated contract
 * @param typeName the name of the financial type
 * @param areaName the name of the business area
 * @param customerName the name of the customer
 * @param category whether the underlying financial type is revenue or a cost
 * @param source whether this row was entered manually or produced by contract-terms generation (read-only — manual writes always force MANUAL server-side, see FinancialValueService)
 */
public record FinancialValueDTO(
        UUID id,
        @Min(value = 1, message = "Il mese va da 1 a 12")
        @Max(value = 12, message = "Il mese va da 1 a 12") int month,
        @Min(value = InputLimits.MIN_YEAR, message = "Anno non valido")
        @Max(value = InputLimits.MAX_YEAR, message = "Anno non valido") int year,
        @DecimalMin(value = InputLimits.MIN_AMOUNT, message = "Importo troppo grande")
        @DecimalMax(value = InputLimits.MAX_AMOUNT, message = "Importo troppo grande") double financialAmount,
        UUID financialTypeId,
        UUID businessAreaId,
        UUID contractId,
        String typeName,
        String areaName,
        String customerName,
        FinancialCategory category,
        FinancialValueSource source
        ) {

    /**
     * Compatibility constructor for call sites predating the manual/generated
     * distinction — defaults {@code source} to {@code MANUAL}.
     */
    public FinancialValueDTO(UUID id, int month, int year, double financialAmount, UUID financialTypeId,
            UUID businessAreaId, UUID contractId, String typeName, String areaName, String customerName,
            FinancialCategory category) {
        this(id, month, year, financialAmount, financialTypeId, businessAreaId, contractId, typeName, areaName,
                customerName, category, FinancialValueSource.MANUAL);
    }
}
