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
        BigDecimal vatAmount,
        SupplierAddress supplierAddress,
        /** ModalitaPagamento of the first payment detail (MP05, MP12...), null without payment terms. */
        String paymentMethod
) {

    /** Without the payment method: call sites predating it. */
    public FatturaPaInvoiceData(String supplierName, String supplierVatNumber, String documentType,
            String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount, String currency,
            List<InvoiceLineItemDTO> lineItems, String supplierIban, String supplierBic, LocalDate paymentDueDate,
            BigDecimal taxableAmount, BigDecimal vatAmount, SupplierAddress supplierAddress) {
        this(supplierName, supplierVatNumber, documentType, invoiceNumber, invoiceDate, totalAmount, currency,
                lineItems, supplierIban, supplierBic, paymentDueDate, taxableAmount, vatAmount, supplierAddress, null);
    }

    /**
     * The supplier's registered office (CedentePrestatore > Sede), mandatory
     * in the schema. Street joins Indirizzo and NumeroCivico; Provincia is
     * optional (absent for a foreign supplier).
     */
    public record SupplierAddress(String street, String postalCode, String city, String province, String country) {
    }

    /** Without the supplier's address: call sites predating it. */
    public FatturaPaInvoiceData(String supplierName, String supplierVatNumber, String documentType,
            String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount, String currency,
            List<InvoiceLineItemDTO> lineItems, String supplierIban, String supplierBic, LocalDate paymentDueDate,
            BigDecimal taxableAmount, BigDecimal vatAmount) {
        this(supplierName, supplierVatNumber, documentType, invoiceNumber, invoiceDate, totalAmount, currency,
                lineItems, supplierIban, supplierBic, paymentDueDate, taxableAmount, vatAmount, null, null);
    }

    /** Compatibility constructor for call sites predating the taxable/VAT split. */
    public FatturaPaInvoiceData(String supplierName, String supplierVatNumber, String documentType,
            String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount, String currency,
            List<InvoiceLineItemDTO> lineItems, String supplierIban, String supplierBic, LocalDate paymentDueDate) {
        this(supplierName, supplierVatNumber, documentType, invoiceNumber, invoiceDate, totalAmount, currency,
                lineItems, supplierIban, supplierBic, paymentDueDate, null, null, null, null);
    }
}
