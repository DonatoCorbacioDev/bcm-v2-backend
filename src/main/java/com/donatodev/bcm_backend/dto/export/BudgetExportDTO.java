/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import com.donatodev.bcm_backend.entity.Budget;
import com.donatodev.bcm_backend.entity.FinancialCategory;

/** One element of {@code data/budgets.json}. */
public record BudgetExportDTO(
        Long id,
        Long businessAreaId,
        FinancialCategory category,
        int year,
        double targetAmount) {

    public static BudgetExportDTO from(Budget budget) {
        return new BudgetExportDTO(
                budget.getId(),
                budget.getBusinessArea().getId(),
                budget.getCategory(),
                budget.getYear(),
                budget.getTargetAmount());
    }
}
