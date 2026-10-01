/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * Thrown when an invoice's supplier IBAN differs from the IBAN already
 * verified/trusted for that counterparty, blocking SEPA batch generation
 * until an admin explicitly confirms the change (see
 * {@code CounterpartyService#confirmVerifiedIban}). Mitigates invoice-IBAN-swap
 * fraud: a compromised or forged invoice should never silently redirect a
 * real payment.
 */
public class IbanMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IbanMismatchException(String message) {
        super(message);
    }
}
