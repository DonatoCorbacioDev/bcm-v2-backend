/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.exception.TierLimitExceededException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;

/**
 * Enforces per-organization resource limits tied to {@link SubscriptionTier}.
 * A tier with no entry in {@link #MAX_ACTIVE_CONTRACTS} (currently
 * {@code ENTERPRISE}) is unlimited. Checks are skipped entirely when there is
 * no tenant context (e.g. single-tenant/legacy data with no organization
 * attached), matching how the rest of the service layer already treats a
 * {@code null} organization id.
 */
@Service
public class TierLimitService {

    private static final Map<SubscriptionTier, Integer> MAX_ACTIVE_CONTRACTS = Map.of(
            SubscriptionTier.FREE, 15,
            SubscriptionTier.PRO, 200
    );

    private final OrganizationRepository organizationRepository;
    private final ContractsRepository contractsRepository;

    public TierLimitService(OrganizationRepository organizationRepository, ContractsRepository contractsRepository) {
        this.organizationRepository = organizationRepository;
        this.contractsRepository = contractsRepository;
    }

    /**
     * Throws {@link TierLimitExceededException} if creating one more contract
     * would push the organization past its tier's limit. No-op when
     * {@code orgId} is {@code null} or the organization can't be resolved.
     *
     * @param orgId the organization attempting to create a contract
     */
    public void enforceContractCreationLimit(Long orgId) {
        if (orgId == null) {
            return;
        }
        Organization organization = organizationRepository.findById(orgId).orElse(null);
        if (organization == null) {
            return;
        }

        Integer limit = MAX_ACTIVE_CONTRACTS.get(organization.getSubscriptionTier());
        if (limit == null) {
            return;
        }

        int currentCount = contractsRepository.countAllContractsByOrg(orgId);
        if (currentCount >= limit) {
            throw new TierLimitExceededException(
                    "Limite del piano " + organization.getSubscriptionTier()
                            + " raggiunto: massimo " + limit + " contratti. Passa a un piano superiore per continuare.");
        }
    }
}
