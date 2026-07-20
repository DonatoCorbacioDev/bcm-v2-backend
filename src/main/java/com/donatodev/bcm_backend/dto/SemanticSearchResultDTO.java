package com.donatodev.bcm_backend.dto;

public record SemanticSearchResultDTO(
        Long contractId,
        String contractNumber,
        String customerName,
        Long documentId,
        String fileName,
        double score
) {}
