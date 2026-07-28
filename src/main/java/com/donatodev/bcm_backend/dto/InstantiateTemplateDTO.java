package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;

import com.donatodev.bcm_backend.entity.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InstantiateTemplateDTO(
        @NotBlank(message = "Nome cliente obbligatorio") String customerName,
        @NotBlank(message = "Numero contratto obbligatorio") String contractNumber,
        String wbsCode,
        String projectName,
        @NotNull(message = "Data inizio obbligatoria") LocalDate startDate,
        LocalDate endDate,
        Long businessAreaId,
        Long managerId,
        ContractStatus status
) {}
