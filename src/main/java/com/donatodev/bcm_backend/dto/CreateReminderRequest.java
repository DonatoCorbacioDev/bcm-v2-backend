/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReminderRequest(
        @NotNull(message = "L'ID del contratto è obbligatorio")
        Long contractId,

        @NotBlank(message = "Il messaggio non può essere vuoto")
        @Size(max = 1000, message = "Il messaggio non può superare 1000 caratteri")
        String message
) {}
