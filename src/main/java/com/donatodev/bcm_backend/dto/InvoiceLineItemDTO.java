/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

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
