package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpCodeRequest(
        @NotBlank(message = "Codice obbligatorio")
        String code
) {}
