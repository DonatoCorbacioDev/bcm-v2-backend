/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

/**
 * Whether a {@link FinancialTypes} (and the {@link FinancialValues}, {@link Budget}
 * records built on top of it) represents money coming in or going out.
 */
public enum FinancialCategory {
    REVENUE,
    COST
}
