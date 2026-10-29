/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;

/** One element of {@code data/financial_types.json}. */
public record FinancialTypeExportDTO(
        Long id,
        String name,
        String description,
        FinancialCategory category) {

    public static FinancialTypeExportDTO from(FinancialTypes type) {
        return new FinancialTypeExportDTO(
                type.getId(),
                type.getName(),
                type.getDescription(),
                type.getCategory());
    }
}
