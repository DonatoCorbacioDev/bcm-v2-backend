package com.donatodev.bcm_backend.dto;

import com.donatodev.bcm_backend.entity.SubscriptionTier;

import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @Size(max = 255, message = "Organization name must not exceed 255 characters")
        String name,

        SubscriptionTier subscriptionTier
) {}
