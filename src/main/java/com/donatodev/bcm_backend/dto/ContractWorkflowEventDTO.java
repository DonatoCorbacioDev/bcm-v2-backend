package com.donatodev.bcm_backend.dto;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.WorkflowAction;
import com.donatodev.bcm_backend.entity.WorkflowStage;

/**
 * A single transition in a contract's approval workflow.
 *
 * @param id the event ID
 * @param fromStage the stage before this transition
 * @param toStage the stage after this transition
 * @param action the action performed (SUBMIT, APPROVE, REJECT)
 * @param actorUsername the username of whoever performed the action
 * @param comment optional comment (required by the API for REJECT)
 * @param createdAt when the transition happened
 */
public record ContractWorkflowEventDTO(
        Long id,
        WorkflowStage fromStage,
        WorkflowStage toStage,
        WorkflowAction action,
        String actorUsername,
        String comment,
        LocalDateTime createdAt
) {
}
