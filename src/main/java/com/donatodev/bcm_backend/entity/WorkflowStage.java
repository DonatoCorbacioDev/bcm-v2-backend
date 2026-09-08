/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

/**
 * Approval workflow stage for a contract still in DRAFT. Only meaningful
 * while {@link Contracts#getStatus()} is {@link ContractStatus#DRAFT} —
 * contracts created directly as ACTIVE/EXPIRED/CANCELLED never enter the
 * workflow and leave this field {@code null}.
 */
public enum WorkflowStage {
    DRAFT,
    IN_REVIEW,
    APPROVED
}
