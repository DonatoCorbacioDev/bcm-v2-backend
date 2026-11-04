/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FatturaPaInvoiceData(
        String supplierName,
        String supplierVatNumber,
        String documentType,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal totalAmount,
        String currency,
        List<InvoiceLineItemDTO> lineItems,
        String supplierIban,
        String supplierBic,
        LocalDate paymentDueDate,
        BigDecimal taxableAmount,
        BigDecimal vatAmount
) {

    /** Compatibility constructor for call sites predating the taxable/VAT split. */
    public FatturaPaInvoiceData(String supplierName, String supplierVatNumber, String documentType,
            String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount, String currency,
            List<InvoiceLineItemDTO> lineItems, String supplierIban, String supplierBic, LocalDate paymentDueDate) {
        this(supplierName, supplierVatNumber, documentType, invoiceNumber, invoiceDate, totalAmount, currency,
                lineItems, supplierIban, supplierBic, paymentDueDate, null, null);
    }
}
