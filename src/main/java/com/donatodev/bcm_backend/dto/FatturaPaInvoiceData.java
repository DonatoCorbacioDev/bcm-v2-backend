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
        List<InvoiceLineItemDTO> lineItems
) {}
