/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.SubscriptionTier;

public record OrganizationDTO(
        Long id,
        String name,
        String slug,
        SubscriptionTier subscriptionTier,
        String iban,
        String bic,
        LocalDateTime createdAt
) {}
