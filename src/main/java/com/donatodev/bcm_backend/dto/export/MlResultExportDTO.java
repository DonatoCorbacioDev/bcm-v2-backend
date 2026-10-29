/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One element of {@code data/ml_analyses.json}: an analysis the Service
 * computed for the organization (risk scores, forecasts, anomalies), keyed as
 * it is cached. {@code result} is the analysis itself as JSON, not a string
 * holding JSON, so it reads like the rest of the export.
 */
public record MlResultExportDTO(String key, JsonNode result, LocalDateTime computedAt) {
}
