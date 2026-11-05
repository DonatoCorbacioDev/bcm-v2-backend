/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

import java.util.Set;

/**
 * What a FatturaPA ModalitaPagamento code means for paying the invoice. Only a
 * bank transfer is paid by BCM, with a SEPA file; RiBa and SEPA direct debits
 * are collected by the supplier itself, everything else is paid outside BCM.
 * An invoice that states no method is treated as a bank transfer, the most
 * common case and what BCM assumed before it read the method.
 */
public final class PaymentMethods {

    public static final String BANK_TRANSFER = "MP05";
    public static final String RIBA = "MP12";
    /** SEPA direct debit: core (MP19), CORE (MP20), B2B (MP21). */
    public static final Set<String> DIRECT_DEBIT = Set.of("MP19", "MP20", "MP21");

    private PaymentMethods() {
    }

    /** Paid by a SEPA credit transfer from BCM: a bank transfer, or no method stated. */
    public static boolean paidByTransfer(String method) {
        return method == null || method.isBlank() || BANK_TRANSFER.equals(method);
    }

    /** Collected by the supplier (RiBa or direct debit): paying it by transfer too would pay it twice. */
    public static boolean collectedBySupplier(String method) {
        return RIBA.equals(method) || DIRECT_DEBIT.contains(method);
    }
}
