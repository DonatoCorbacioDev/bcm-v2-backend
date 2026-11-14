/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.time.LocalDate;

import com.donatodev.bcm_backend.entity.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InstantiateTemplateDTO(
        @NotNull(message = "Controparte obbligatoria") UUID counterpartyId,
        @NotBlank(message = "Numero contratto obbligatorio")
        @Size(max = 255, message = "Il numero contratto non può superare 255 caratteri") String contractNumber,
        @Size(max = 50, message = "Il codice WBS non può superare 50 caratteri") String wbsCode,
        @Size(max = 100, message = "Il nome progetto non può superare 100 caratteri") String projectName,
        @NotNull(message = "Data inizio obbligatoria") LocalDate startDate,
        LocalDate endDate,
        UUID businessAreaId,
        UUID managerId,
        ContractStatus status
) {}
