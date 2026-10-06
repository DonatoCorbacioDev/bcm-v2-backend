/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

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
        Long id,
        Long contractId,
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
        Long sepaBatchId,
        InvoiceMatchStatus matchStatus,
        Long matchedFinancialValueId,
        Double matchConfidence,
        Instant matchedAt,
        String matchedByUsername
) {}
