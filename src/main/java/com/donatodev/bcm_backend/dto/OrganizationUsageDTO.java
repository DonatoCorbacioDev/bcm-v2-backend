/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

/**
 * @param contractsUsed  number of contracts currently counted against the
 *                       organization's tier limit (see
 *                       {@code TierLimitService#enforceContractCreationLimit})
 * @param contractsLimit the tier's maximum, or {@code null} if unlimited
 *                       (see {@code TierLimitService#getContractLimit})
 */
public record OrganizationUsageDTO(
        int contractsUsed,
        Integer contractsLimit
) {}
