/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SubscriptionTier;

/**
 * {@code data/organization.json}. Unlike {@code OrganizationDTO} the IBAN is
 * not masked: the export is the customer's own copy of its data. The Stripe
 * customer id is left out because it identifies the customer in our billing
 * account, not anything the customer entered.
 */
public record OrganizationExportDTO(
        Long id,
        String name,
        String slug,
        SubscriptionTier subscriptionTier,
        String iban,
        String bic,
        String termsVersion,
        LocalDateTime termsAcceptedAt,
        LocalDateTime createdAt) {

    public static OrganizationExportDTO from(Organization org) {
        return new OrganizationExportDTO(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getSubscriptionTier(),
                org.getIban(),
                org.getBic(),
                org.getTermsVersion(),
                org.getTermsAcceptedAt(),
                org.getCreatedAt());
    }
}
