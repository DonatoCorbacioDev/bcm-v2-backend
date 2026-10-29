/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.ContractTemplate;
import com.donatodev.bcm_backend.entity.Managers;

/** One element of {@code data/contract_templates.json}. */
public record ContractTemplateExportDTO(
        Long id,
        String name,
        String description,
        ContractStatus defaultStatus,
        Integer defaultDurationDays,
        Long businessAreaId,
        Long defaultManagerId,
        boolean autoRenew,
        Integer notificationDays,
        LocalDateTime createdAt) {

    public static ContractTemplateExportDTO from(ContractTemplate template) {
        return new ContractTemplateExportDTO(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getDefaultStatus(),
                template.getDefaultDurationDays(),
                ExportIds.idOf(template.getBusinessArea(), BusinessAreas::getId),
                ExportIds.idOf(template.getDefaultManager(), Managers::getId),
                template.isAutoRenew(),
                template.getNotificationDays(),
                template.getCreatedAt());
    }
}
