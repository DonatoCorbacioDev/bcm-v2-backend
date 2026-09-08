/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;

import com.donatodev.bcm_backend.entity.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InstantiateTemplateDTO(
        @NotNull(message = "Controparte obbligatoria") Long counterpartyId,
        @NotBlank(message = "Numero contratto obbligatorio") String contractNumber,
        String wbsCode,
        String projectName,
        @NotNull(message = "Data inizio obbligatoria") LocalDate startDate,
        LocalDate endDate,
        Long businessAreaId,
        Long managerId,
        ContractStatus status
) {}
