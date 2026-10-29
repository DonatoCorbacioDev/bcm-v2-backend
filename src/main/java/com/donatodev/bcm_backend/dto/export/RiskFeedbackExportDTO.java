/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.RiskFeedback;

/** One element of {@code data/risk_feedback.json}. */
public record RiskFeedbackExportDTO(
        Long id,
        Long contractId,
        Long submittedByUserId,
        double riskScore,
        String riskLevel,
        Double mlScore,
        String mlLevel,
        boolean agree,
        LocalDateTime createdAt) {

    public static RiskFeedbackExportDTO from(RiskFeedback feedback) {
        return new RiskFeedbackExportDTO(
                feedback.getId(),
                feedback.getContract().getId(),
                feedback.getSubmittedBy().getId(),
                feedback.getRiskScore(),
                feedback.getRiskLevel(),
                feedback.getMlScore(),
                feedback.getMlLevel(),
                feedback.isAgree(),
                feedback.getCreatedAt());
    }
}
