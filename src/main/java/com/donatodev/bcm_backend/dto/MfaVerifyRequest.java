package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequest(
        @NotBlank(message = "Token MFA obbligatorio")
        String mfaToken,

        @NotBlank(message = "Codice obbligatorio")
        String code
) {}
