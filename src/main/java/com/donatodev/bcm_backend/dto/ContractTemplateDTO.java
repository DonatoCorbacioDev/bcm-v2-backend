package com.donatodev.bcm_backend.dto;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.ContractStatus;
import jakarta.validation.constraints.NotBlank;

public record ContractTemplateDTO(
        Long id,
        @NotBlank(message = "Nome modello obbligatorio") String name,
        String description,
        ContractStatus defaultStatus,
        Integer defaultDurationDays,
        Long businessAreaId,
        Long defaultManagerId,
        boolean autoRenew,
        Integer notificationDays,
        LocalDateTime createdAt
) {}
