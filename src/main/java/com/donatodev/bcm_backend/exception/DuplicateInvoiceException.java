/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when an uploaded FatturaPA invoice matches one already
 * stored for the same organization (same supplier, invoice number and
 * document type).
 */
public class DuplicateInvoiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateInvoiceException(String message) {
        super(message);
    }
}
