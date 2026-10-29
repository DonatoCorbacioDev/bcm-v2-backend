/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * The organization can't be scheduled for deletion in its current state: a
 * deletion is already pending, or a paid subscription is still active (Stripe
 * would keep charging a customer that no longer exists). Answers 409.
 */
public class OrganizationDeletionConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OrganizationDeletionConflictException(String message) {
        super(message);
    }
}
