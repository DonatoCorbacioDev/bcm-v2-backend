/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

/**
 * Where a {@link FinancialValues} row came from. {@code MANUAL} rows are
 * created/edited by a user through the normal CRUD endpoints and are never
 * touched by contract-terms generation; {@code GENERATED} rows are produced
 * by {@code ContractFinancialGenerationService} from a contract's financial
 * terms and may be deleted/recreated on regeneration (except for periods
 * already in the past — see that service for the exact rule).
 */
public enum FinancialValueSource {
    MANUAL,
    GENERATED
}
