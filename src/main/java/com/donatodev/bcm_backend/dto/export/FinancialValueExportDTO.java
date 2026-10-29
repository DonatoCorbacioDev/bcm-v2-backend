/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import com.donatodev.bcm_backend.entity.FinancialValueSource;
import com.donatodev.bcm_backend.entity.FinancialValues;

/** One element of {@code data/financial_values.json}. */
public record FinancialValueExportDTO(
        Long id,
        Long contractId,
        Long financialTypeId,
        Long businessAreaId,
        int month,
        int year,
        double financialAmount,
        FinancialValueSource source) {

    public static FinancialValueExportDTO from(FinancialValues value) {
        return new FinancialValueExportDTO(
                value.getId(),
                value.getContract().getId(),
                value.getFinancialType().getId(),
                value.getBusinessArea().getId(),
                value.getMonth(),
                value.getYear(),
                value.getFinancialAmount(),
                value.getSource());
    }
}
