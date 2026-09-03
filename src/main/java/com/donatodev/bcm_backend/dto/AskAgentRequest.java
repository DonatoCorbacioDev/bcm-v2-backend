package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AskAgentRequest(
        @NotBlank(message = "La domanda non può essere vuota")
        String question
) {}
