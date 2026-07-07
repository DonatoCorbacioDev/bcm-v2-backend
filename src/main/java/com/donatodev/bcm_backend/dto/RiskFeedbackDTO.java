package com.donatodev.bcm_backend.dto;

import java.time.LocalDateTime;

public record RiskFeedbackDTO(
        Long id,
        Long contractId,
        double riskScore,
        String riskLevel,
        Double mlScore,
        String mlLevel,
        boolean agree,
        LocalDateTime createdAt
) {}
