/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.InvoiceLineItem;
import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;
import com.donatodev.bcm_backend.entity.SepaPaymentBatch;
import com.donatodev.bcm_backend.entity.Users;

/**
 * One element of {@code data/electronic_invoices.json}. The lines are nested
 * because they only exist as part of their invoice. The storage path is internal.
 */
public record ElectronicInvoiceExportDTO(
        Long id,
        Long contractId,
        String fileName,
        Long fileSize,
        String contentType,
        Instant uploadedAt,
        String supplierName,
        String supplierVatNumber,
        String documentType,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal totalAmount,
        String currency,
        String supplierIban,
        String supplierBic,
        LocalDate paymentDueDate,
        Long sepaBatchId,
        Long matchedFinancialValueId,
        Double matchConfidence,
        InvoiceMatchStatus matchStatus,
        Instant matchedAt,
        Long matchedByUserId,
        List<LineItem> lineItems) {

    public record LineItem(
            Integer lineNumber,
            String description,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            BigDecimal vatRate) {

        static LineItem from(InvoiceLineItem line) {
            return new LineItem(
                    line.getLineNumber(),
                    line.getDescription(),
                    line.getQuantity(),
                    line.getUnitOfMeasure(),
                    line.getUnitPrice(),
                    line.getTotalPrice(),
                    line.getVatRate());
        }
    }

    public static ElectronicInvoiceExportDTO from(ElectronicInvoice invoice) {
        return new ElectronicInvoiceExportDTO(
                invoice.getId(),
                invoice.getContract().getId(),
                invoice.getFileName(),
                invoice.getFileSize(),
                invoice.getContentType(),
                invoice.getUploadedAt(),
                invoice.getSupplierName(),
                invoice.getSupplierVatNumber(),
                invoice.getDocumentType(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getSupplierIban(),
                invoice.getSupplierBic(),
                invoice.getPaymentDueDate(),
                ExportIds.idOf(invoice.getSepaBatch(), SepaPaymentBatch::getId),
                ExportIds.idOf(invoice.getMatchedFinancialValue(), FinancialValues::getId),
                invoice.getMatchConfidence(),
                invoice.getMatchStatus(),
                invoice.getMatchedAt(),
                ExportIds.idOf(invoice.getMatchedByUser(), Users::getId),
                invoice.getLineItems().stream().map(LineItem::from).toList());
    }
}
