/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param registrationId id returned when the registration was requested
 */
public record ResendRegistrationCodeRequest(

        @NotBlank(message = "Registrazione non indicata")
        @Size(max = 64, message = "Registrazione non valida")
        String registrationId
) {}
