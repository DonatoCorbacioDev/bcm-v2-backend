package com.donatodev.bcm_backend.dto;

import com.donatodev.bcm_backend.entity.SubscriptionTier;

import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @Size(max = 255, message = "Il nome organizzazione non può superare 255 caratteri")
        String name,

        SubscriptionTier subscriptionTier,

        @Size(max = 34, message = "L'IBAN non può superare 34 caratteri")
        String iban,

        @Size(max = 11, message = "Il BIC non può superare 11 caratteri")
        String bic
) {}
