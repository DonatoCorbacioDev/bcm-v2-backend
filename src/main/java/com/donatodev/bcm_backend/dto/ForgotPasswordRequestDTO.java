package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO used to handle forgot password requests.
 * <p>
 * Validates that a non-empty and properly formatted email is provided.
 *
 * @param email the user's email address
 */
public record ForgotPasswordRequestDTO(
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email
) {}
