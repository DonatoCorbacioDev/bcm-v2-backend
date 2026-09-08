/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.List;

/**
 * Summary of one {@code ContractFinancialGenerationService} run, returned by
 * the manual "regenerate" endpoint so the caller can see the actual blast
 * radius of the click (how many rows were created, how many past-vs-future
 * GENERATED rows were replaced, how many periods were left alone because a
 * MANUAL row already occupied that slot) instead of discovering it later in
 * a dashboard.
 *
 * @param created number of new {@code FinancialValues} rows inserted
 * @param regenerated number of existing GENERATED rows (month/year >= today) that were deleted and replaced
 * @param skippedManual number of periods left untouched because a MANUAL row already occupies that slot
 * @param values the rows created by this run
 */
public record FinancialGenerationResultDTO(
        int created,
        int regenerated,
        int skippedManual,
        List<FinancialValueDTO> values
        ) {
}
