/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import com.donatodev.bcm_backend.validation.InputLimits;

public record ContractTemplateDTO(
        UUID id,
        @NotBlank(message = "Nome modello obbligatorio")
        @Size(max = 255, message = "Il nome non può superare 255 caratteri") String name,
        @Size(max = InputLimits.LONG_TEXT, message = "La descrizione è troppo lunga") String description,
        ContractStatus defaultStatus,
        @Min(value = 1, message = "La durata deve essere di almeno 1 giorno")
        @Max(value = InputLimits.MAX_DAYS, message = "La durata è troppo lunga") Integer defaultDurationDays,
        UUID businessAreaId,
        UUID defaultManagerId,
        boolean autoRenew,
        @Min(value = 0, message = "Il preavviso non può essere negativo")
        @Max(value = 3650, message = "Il preavviso non può superare i 10 anni") Integer notificationDays,
        LocalDateTime createdAt
) {}
