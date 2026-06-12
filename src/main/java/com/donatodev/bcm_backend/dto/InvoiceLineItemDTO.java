package com.donatodev.bcm_backend.dto;

import java.math.BigDecimal;

public record InvoiceLineItemDTO(
        Integer lineNumber,
        String description,
        BigDecimal quantity,
        String unitOfMeasure,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BigDecimal vatRate
) {}
