/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.time.Instant;

public record ContractDocumentDTO(
        UUID id,
        UUID contractId,
        String fileName,
        Long fileSize,
        String contentType,
        Instant uploadedAt,
        String downloadUrl,
        UUID versionGroupId,
        Integer versionNumber,
        int versionCount
) {}
