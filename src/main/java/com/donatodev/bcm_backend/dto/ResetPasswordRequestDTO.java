package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO used to handle password reset requests via REST API.
 * <p>
 * Carries the token received by email and the new password to be set.
 *
 * @param token        the unique token sent to the user's email for verification
 * @param newPassword  the new password to be set for the user account
 */
public record ResetPasswordRequestDTO(
        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "New password is required")
        String newPassword
) {}
