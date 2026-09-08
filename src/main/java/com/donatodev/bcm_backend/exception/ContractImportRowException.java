/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * Thrown when a single row of a bulk contract import file fails validation
 * or field resolution. Caught per-row by {@code ContractImportService} and
 * turned into a {@code ContractImportRowError} instead of aborting the
 * whole import.
 */
public class ContractImportRowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContractImportRowException(String message) {
        super(message);
    }
}
