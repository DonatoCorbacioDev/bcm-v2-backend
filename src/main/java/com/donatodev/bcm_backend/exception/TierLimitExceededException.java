/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * Thrown when an organization tries to create a resource beyond what its
 * {@code SubscriptionTier} allows (see {@code TierLimitService}). Distinct
 * from an authorization failure: the caller is allowed to perform the
 * action in general, just not any more times on their current plan.
 */
public class TierLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TierLimitExceededException(String message) {
        super(message);
    }
}
