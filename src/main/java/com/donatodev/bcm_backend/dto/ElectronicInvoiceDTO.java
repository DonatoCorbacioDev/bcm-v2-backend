/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;

/**
 * @param supplierIban masked ({@code "IT...3456"}) via
 *                      {@code ElectronicInvoiceService#toDTO} -- never the
 *                      full value; stays {@code null} when absent (presence
 *                      alone drives UI badges, so it isn't replaced with a
 *                      placeholder). {@code UpdateInvoicePaymentDetailsRequest
 *                      .supplierIban} carries the real value on write;
 *                      {@code null} there means "leave unchanged".
 * @param supplierBic   same masking as {@code supplierIban}
 */
public record ElectronicInvoiceDTO(
        UUID id,
        UUID contractId,
        String fileName,
        Long fileSize,
        Instant uploadedAt,
        String downloadUrl,
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
        UUID sepaBatchId,
        InvoiceMatchStatus matchStatus,
        UUID matchedFinancialValueId,
        Double matchConfidence,
        Instant matchedAt,
        String matchedByUsername,
        BigDecimal taxableAmount,
        BigDecimal vatAmount,
        BigDecimal expectedAmount,
        BigDecimal amountDifference,
        // The invoice asks to be paid on an IBAN other than the one verified
        // for its supplier: SEPA payment is blocked until someone checks it.
        boolean ibanChanged,
        // FatturaPA ModalitaPagamento (MP05 bank transfer, MP12 RiBa, MP19-21
        // direct debit...), null when the invoice states none.
        String paymentMethod
) {

    /** Without the payment method: call sites predating it. */
    public ElectronicInvoiceDTO(UUID id, UUID contractId, String fileName, Long fileSize, Instant uploadedAt,
            String downloadUrl, String supplierName, String supplierVatNumber, String documentType,
            String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount, String currency,
            List<InvoiceLineItemDTO> lineItems, String supplierIban, String supplierBic, LocalDate paymentDueDate,
            UUID sepaBatchId, InvoiceMatchStatus matchStatus, UUID matchedFinancialValueId, Double matchConfidence,
            Instant matchedAt, String matchedByUsername, BigDecimal taxableAmount, BigDecimal vatAmount,
            BigDecimal expectedAmount, BigDecimal amountDifference, boolean ibanChanged) {
        this(id, contractId, fileName, fileSize, uploadedAt, downloadUrl, supplierName, supplierVatNumber,
                documentType, invoiceNumber, invoiceDate, totalAmount, currency, lineItems, supplierIban,
                supplierBic, paymentDueDate, sepaBatchId, matchStatus, matchedFinancialValueId, matchConfidence,
                matchedAt, matchedByUsername, taxableAmount, vatAmount, expectedAmount, amountDifference,
                ibanChanged, null);
    }

    /** Without the IBAN check: call sites predating it. */
    public ElectronicInvoiceDTO(UUID id, UUID contractId, String fileName, Long fileSize, Instant uploadedAt,
            String downloadUrl, String supplierName, String supplierVatNumber, String documentType,
            String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount, String currency,
            List<InvoiceLineItemDTO> lineItems, String supplierIban, String supplierBic, LocalDate paymentDueDate,
            UUID sepaBatchId, InvoiceMatchStatus matchStatus, UUID matchedFinancialValueId, Double matchConfidence,
            Instant matchedAt, String matchedByUsername, BigDecimal taxableAmount, BigDecimal vatAmount,
            BigDecimal expectedAmount, BigDecimal amountDifference) {
        this(id, contractId, fileName, fileSize, uploadedAt, downloadUrl, supplierName, supplierVatNumber,
                documentType, invoiceNumber, invoiceDate, totalAmount, currency, lineItems, supplierIban,
                supplierBic, paymentDueDate, sepaBatchId, matchStatus, matchedFinancialValueId, matchConfidence,
                matchedAt, matchedByUsername, taxableAmount, vatAmount, expectedAmount, amountDifference, false);
    }

    /** Compatibility constructor for call sites predating the taxable amount comparison. */
    public ElectronicInvoiceDTO(UUID id, UUID contractId, String fileName, Long fileSize, Instant uploadedAt,
            String downloadUrl, String supplierName, String supplierVatNumber, String documentType,
            String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount, String currency,
            List<InvoiceLineItemDTO> lineItems, String supplierIban, String supplierBic, LocalDate paymentDueDate,
            UUID sepaBatchId, InvoiceMatchStatus matchStatus, UUID matchedFinancialValueId, Double matchConfidence,
            Instant matchedAt, String matchedByUsername) {
        this(id, contractId, fileName, fileSize, uploadedAt, downloadUrl, supplierName, supplierVatNumber,
                documentType, invoiceNumber, invoiceDate, totalAmount, currency, lineItems, supplierIban,
                supplierBic, paymentDueDate, sepaBatchId, matchStatus, matchedFinancialValueId, matchConfidence,
                matchedAt, matchedByUsername, null, null, null, null);
    }
}
