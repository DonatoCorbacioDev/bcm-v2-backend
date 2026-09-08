/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.time.Instant;

public record ContractDocumentDTO(
        Long id,
        Long contractId,
        String fileName,
        Long fileSize,
        String contentType,
        Instant uploadedAt,
        String downloadUrl,
        Long versionGroupId,
        Integer versionNumber,
        int versionCount
) {}
