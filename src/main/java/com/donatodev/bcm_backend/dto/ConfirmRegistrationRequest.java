/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Typing the emailed code back confirms the registration and signs the
 * specific approval of the onerous clauses.
 *
 * @param registrationId id returned when the registration was requested
 * @param code           the 6-digit code from the email
 */
public record ConfirmRegistrationRequest(

        @NotBlank(message = "Registrazione non indicata")
        String registrationId,

        @NotBlank(message = "Codice di conferma obbligatorio")
        @Pattern(regexp = "\\d{6}", message = "Il codice di conferma è composto da 6 cifre")
        String code
) {}
