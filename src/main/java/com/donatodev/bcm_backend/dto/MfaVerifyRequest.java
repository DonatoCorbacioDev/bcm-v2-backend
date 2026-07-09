package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequest(
        @NotBlank(message = "MFA token is required")
        String mfaToken,

        @NotBlank(message = "Code is required")
        String code
) {}
