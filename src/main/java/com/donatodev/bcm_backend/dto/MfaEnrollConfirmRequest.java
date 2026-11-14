/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.donatodev.bcm_backend.validation.InputLimits;

public record MfaEnrollConfirmRequest(
        @NotBlank(message = "Token di configurazione obbligatorio")
        @Size(max = InputLimits.TOKEN, message = "Token non valido")
        String setupToken,
        @NotBlank(message = "Codice obbligatorio")
        @Size(max = 10, message = "Codice non valido")
        String code
) {}
