/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.donatodev.bcm_backend.validation.InputLimits;

/**
 * Data Transfer Object (DTO) for authentication requests.
 * <p>
 * Used to encapsulate the user's login credentials.
 *
 * @param username the user's email or username
 * @param password the user's password
 * @param organizationSlug optional slug identifying the organization the user belongs to,
 *                          used to disambiguate usernames shared across organizations
 */
public record AuthRequestDTO(
        @NotBlank(message = "Username is required")
        @Size(max = 255, message = "Username non valido") String username,
        @NotBlank(message = "Password is required")
        @Size(max = InputLimits.PASSWORD_CHECKED, message = "Password non valida") String password,
        @Size(max = 100, message = "Organizzazione non valida") String organizationSlug
) {
    public AuthRequestDTO(String username, String password) {
        this(username, password, null);
    }
}
