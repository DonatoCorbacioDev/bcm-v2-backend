package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for confirming or disputing a risk score shown for a contract.
 * The score/level fields echo what was displayed to the reviewer, so the
 * stored feedback stays meaningful even after the live score later changes.
 */
public record RiskFeedbackRequest(

        @NotNull(message = "Punteggio di rischio obbligatorio")
        Double riskScore,

        @NotBlank(message = "Livello di rischio obbligatorio")
        String level,

        Double mlScore,

        String mlLevel,

        @NotNull(message = "Conferma obbligatoria")
        Boolean agree
) {}
