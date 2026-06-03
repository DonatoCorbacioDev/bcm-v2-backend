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
