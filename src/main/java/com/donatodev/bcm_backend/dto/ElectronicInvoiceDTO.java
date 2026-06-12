package com.donatodev.bcm_backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        List<InvoiceLineItemDTO> lineItems
) {}
