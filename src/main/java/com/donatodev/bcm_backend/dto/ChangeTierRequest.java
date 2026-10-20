/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import com.donatodev.bcm_backend.entity.SubscriptionTier;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /internal/organizations/{id}/tier}.
 *
 * @param tier the new subscription tier
 */
public record ChangeTierRequest(@NotNull(message = "Il piano è obbligatorio") SubscriptionTier tier) {
}
