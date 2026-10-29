/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.ContractWorkflowEvent;
import com.donatodev.bcm_backend.entity.WorkflowAction;
import com.donatodev.bcm_backend.entity.WorkflowStage;

/** One element of {@code data/contract_workflow_events.json}. */
public record ContractWorkflowEventExportDTO(
        Long id,
        Long contractId,
        WorkflowStage fromStage,
        WorkflowStage toStage,
        WorkflowAction action,
        Long actorUserId,
        String comment,
        LocalDateTime createdAt) {

    public static ContractWorkflowEventExportDTO from(ContractWorkflowEvent event) {
        return new ContractWorkflowEventExportDTO(
                event.getId(),
                event.getContract().getId(),
                event.getFromStage(),
                event.getToStage(),
                event.getAction(),
                event.getActor().getId(),
                event.getComment(),
                event.getCreatedAt());
    }
}
