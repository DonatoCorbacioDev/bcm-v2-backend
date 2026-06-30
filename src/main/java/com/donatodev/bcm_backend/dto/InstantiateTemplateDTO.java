package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;

import com.donatodev.bcm_backend.entity.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InstantiateTemplateDTO(
        @NotBlank(message = "Customer name is required") String customerName,
        @NotBlank(message = "Contract number is required") String contractNumber,
        String wbsCode,
        String projectName,
        @NotNull(message = "Start date is required") LocalDate startDate,
        LocalDate endDate,
        Long businessAreaId,
        Long managerId,
        ContractStatus status
) {}
