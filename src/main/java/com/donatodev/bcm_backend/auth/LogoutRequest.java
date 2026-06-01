package com.donatodev.bcm_backend.auth;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh token must not be blank")
        String refreshToken
) {}
