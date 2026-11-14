/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for confirming or disputing a risk score shown for a contract.
 * The score/level fields echo what was displayed to the reviewer, so the
 * stored feedback stays meaningful even after the live score later changes.
 */
public record RiskFeedbackRequest(

        @NotNull(message = "Punteggio di rischio obbligatorio")
        @DecimalMin(value = "0", message = "Il punteggio va da 0 a 1")
        @DecimalMax(value = "1", message = "Il punteggio va da 0 a 1")
        Double riskScore,
        @NotBlank(message = "Livello di rischio obbligatorio")
        @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "Livello di rischio non valido")
        String level,
        @DecimalMin(value = "0", message = "Il punteggio va da 0 a 1")
        @DecimalMax(value = "1", message = "Il punteggio va da 0 a 1")
        Double mlScore,
        @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "Livello di rischio non valido")
        String mlLevel,

        @NotNull(message = "Conferma obbligatoria")
        Boolean agree
) {}
