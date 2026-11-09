/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.time.LocalDateTime;

public record RiskFeedbackDTO(
        UUID id,
        UUID contractId,
        double riskScore,
        String riskLevel,
        Double mlScore,
        String mlLevel,
        boolean agree,
        LocalDateTime createdAt
) {}
