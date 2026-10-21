/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.SubscriptionTier;

/**
 * @param iban masked ({@code "IT...3456"}) via {@code OrganizationService#toDTO}
 *             -- never the full value. {@code UpdateOrganizationRequest.iban}
 *             carries the real value on write; {@code null} there means
 *             "leave unchanged", so this masked read-side value is never
 *             round-tripped back as an edit.
 * @param bic  same masking as {@code iban}
 * @param billingPortalAvailable whether the org has a Stripe customer, i.e. a
 *             subscription the admin can manage in the Stripe Customer Portal.
 *             A boolean on purpose: the {@code cus_...} id itself never
 *             leaves the backend.
 */
public record OrganizationDTO(
        Long id,
        String name,
        String slug,
        SubscriptionTier subscriptionTier,
        String iban,
        String bic,
        LocalDateTime createdAt,
        boolean billingPortalAvailable
) {}
