/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

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
        @NotBlank(message = "Token obbligatorio")
        String token,

        @NotBlank(message = "Nuova password obbligatoria")
        String newPassword
) {}
