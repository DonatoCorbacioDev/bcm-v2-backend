package com.donatodev.bcm_backend.dto;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.SubscriptionTier;

public record OrganizationDTO(
        Long id,
        String name,
        String slug,
        SubscriptionTier subscriptionTier,
        LocalDateTime createdAt
) {}
