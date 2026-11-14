/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.donatodev.bcm_backend.validation.InputLimits;

/**
 * Data Transfer Object for Budget.
 * <p>
 * Represents a yearly revenue or cost target for a business area.
 * {@code actualAmount} and {@code percentUsed} are computed from the
 * matching financial values at read time, never persisted, and ignored
 * when the DTO is used as a create/update request body.
 *
 * @param id            the unique identifier of the budget
 * @param businessAreaId the ID of the related business area
 * @param areaName      the name of the business area
 * @param category      whether this budget targets revenue or cost
 * @param year          the year the budget applies to
 * @param targetAmount  the target amount set for the year
 * @param actualAmount  the sum of financial values matching area/category/year
 * @param percentUsed   actualAmount as a percentage of targetAmount (0 if targetAmount is 0)
 */
public record BudgetDTO(
        UUID id,
        @NotNull(message = "Area aziendale obbligatoria") UUID businessAreaId,
        String areaName,
        @NotNull(message = "Categoria obbligatoria") FinancialCategory category,
        @Min(value = InputLimits.MIN_YEAR, message = "Anno non valido")
        @Max(value = InputLimits.MAX_YEAR, message = "Anno non valido") int year,
        @DecimalMin(value = "0", message = "L'obiettivo non può essere negativo")
        @DecimalMax(value = InputLimits.MAX_AMOUNT, message = "Importo troppo grande") double targetAmount,
        double actualAmount,
        double percentUsed
) {}
