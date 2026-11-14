/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO used to handle forgot password requests.
 * <p>
 * Validates that a non-empty and properly formatted email is provided.
 *
 * @param email the user's email address
 */
public record ForgotPasswordRequestDTO(
        @Email(message = "Formato email non valido")
        @NotBlank(message = "Email obbligatoria")
        @Size(max = 255, message = "Email non valida")
        String email
) {}
