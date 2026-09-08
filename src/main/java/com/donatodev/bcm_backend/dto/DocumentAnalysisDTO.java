/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

public record DocumentAnalysisDTO(
        Long documentId,
        String rawText,
        String detectedCustomerName,
        String detectedContractNumber,
        String detectedStartDate,
        String detectedEndDate,
        String detectedAmount
) {}
