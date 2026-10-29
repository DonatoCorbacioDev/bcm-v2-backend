/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.Instant;

import com.donatodev.bcm_backend.entity.ContractDocument;

/**
 * One element of {@code data/contract_documents.json}: metadata of an uploaded
 * contract document, whose original is at {@code archivePath}. The storage
 * path is internal; the embedding is a product of our model (Terms sez. 13
 * excludes the provider's models), while the extracted text is the
 * document's own content.
 */
public record ContractDocumentExportDTO(
        Long id,
        Long contractId,
        String archivePath,
        String fileName,
        Long fileSize,
        String contentType,
        Long versionGroupId,
        Integer versionNumber,
        String extractedText,
        Instant uploadedAt) {

    public static ContractDocumentExportDTO from(ContractDocument document) {
        return new ContractDocumentExportDTO(
                document.getId(),
                document.getContract().getId(),
                ExportFilePaths.of(document),
                document.getFileName(),
                document.getFileSize(),
                document.getContentType(),
                document.getVersionGroupId(),
                document.getVersionNumber(),
                document.getExtractedText(),
                document.getUploadedAt());
    }
}
