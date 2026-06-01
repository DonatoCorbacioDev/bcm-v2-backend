package com.donatodev.bcm_backend.dto;

import java.time.Instant;

public record ContractDocumentDTO(
        Long id,
        Long contractId,
        String fileName,
        Long fileSize,
        String contentType,
        Instant uploadedAt,
        String downloadUrl
) {}
