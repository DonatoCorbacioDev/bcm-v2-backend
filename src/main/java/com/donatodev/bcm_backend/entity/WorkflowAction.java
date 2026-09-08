/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

/**
 * Action recorded by a {@link ContractWorkflowEvent}.
 */
public enum WorkflowAction {
    SUBMIT,
    APPROVE,
    REJECT
}
