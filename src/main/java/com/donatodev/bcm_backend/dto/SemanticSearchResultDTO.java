/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;

public record SemanticSearchResultDTO(
        UUID contractId,
        String contractNumber,
        String customerName,
        UUID documentId,
        String fileName,
        double score
) {}
