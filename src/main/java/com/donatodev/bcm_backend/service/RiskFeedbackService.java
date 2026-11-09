/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.UUID;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.RiskFeedbackDTO;
import com.donatodev.bcm_backend.dto.RiskFeedbackRequest;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.RiskFeedback;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.RiskFeedbackMapper;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.RiskFeedbackRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;

/**
 * Business logic for recording and retrieving human feedback on ML/heuristic
 * risk scores, with the same admin-sees-all / manager-sees-own-contracts
 * visibility rule used elsewhere in the app.
 */
@Service
public class RiskFeedbackService {

    private static final String USER_NOT_FOUND_MSG = "Utente non trovato";
    private static final String ADMIN_ROLE = "ADMIN";

    private final RiskFeedbackRepository riskFeedbackRepository;
    private final ContractsRepository contractsRepository;
    private final UsersRepository usersRepository;
    private final RiskFeedbackMapper riskFeedbackMapper;

    public RiskFeedbackService(
            RiskFeedbackRepository riskFeedbackRepository,
            ContractsRepository contractsRepository,
            UsersRepository usersRepository,
            RiskFeedbackMapper riskFeedbackMapper) {
        this.riskFeedbackRepository = riskFeedbackRepository;
        this.contractsRepository = contractsRepository;
        this.usersRepository = usersRepository;
        this.riskFeedbackMapper = riskFeedbackMapper;
    }

    /**
     * Records whether the authenticated user agreed with the risk score shown
     * for a contract. Managers may only submit feedback for their own contracts.
     *
     * @param contractId the contract the feedback refers to
     * @param request the score/level shown and whether the reviewer agreed with it
     * @return the persisted feedback entry
     * @throws ContractNotFoundException if the contract does not exist in scope
     * @throws AccessDeniedException if a manager targets a contract they don't own
     */
    public RiskFeedbackDTO create(UUID contractId, RiskFeedbackRequest request) {
        Users user = getAuthenticatedUser();
        Contracts contract = findContractInScope(contractId)
                .orElseThrow(() -> new ContractNotFoundException("Contratto non trovato: " + contractId));

        if (!isAdmin(user) && !isOwnedByManager(contract, user)) {
            throw new AccessDeniedException("non autorizzato a inviare feedback per questo contratto");
        }

        UUID orgId = contract.getOrganization() != null ? contract.getOrganization().getId() : TenantContext.get();

        RiskFeedback feedback = RiskFeedback.builder()
                .contract(contract)
                .submittedBy(user)
                .organizationId(orgId)
                .riskScore(request.riskScore())
                .riskLevel(request.level())
                .mlScore(request.mlScore())
                .mlLevel(request.mlLevel())
                .agree(Boolean.TRUE.equals(request.agree()))
                .build();

        return riskFeedbackMapper.toDTO(riskFeedbackRepository.save(feedback));
    }

    /**
     * Retrieves the most recent feedback entry per contract, visible to the
     * authenticated user (all contracts for admins, only assigned ones for managers).
     *
     * @return one {@link RiskFeedbackDTO} per contract that has feedback
     */
    public List<RiskFeedbackDTO> getFeedbackForCurrentUser() {
        Users user = getAuthenticatedUser();

        List<RiskFeedback> all;
        if (isAdmin(user)) {
            UUID orgId = TenantContext.require();
            all = riskFeedbackRepository.findByOrganizationIdOrderByCreatedAtDescIdDesc(orgId);
        } else {
            all = riskFeedbackRepository.findByContractManagerIdOrderByCreatedAtDescIdDesc(user.getManager().getId());
        }

        // Sort in Java too, so the dedupe below never depends on the query's
        // ORDER BY. Id is a tiebreaker for rows sharing the same createdAt
        // (two submissions can land in the same clock tick).
        List<RiskFeedback> mostRecentFirst = all.stream()
                .sorted(Comparator.comparing(RiskFeedback::getCreatedAt)
                        .thenComparing(RiskFeedback::getId)
                        .reversed())
                .toList();

        Map<UUID, RiskFeedback> latestByContract = new LinkedHashMap<>();
        for (RiskFeedback feedback : mostRecentFirst) {
            latestByContract.putIfAbsent(feedback.getContract().getId(), feedback);
        }
        return latestByContract.values().stream().map(riskFeedbackMapper::toDTO).toList();
    }

    private boolean isAdmin(Users user) {
        return ADMIN_ROLE.equals(user.getRole().getRole());
    }

    private boolean isOwnedByManager(Contracts contract, Users user) {
        return contract.getManager() != null
                && user.getManager() != null
                && contract.getManager().getId().equals(user.getManager().getId());
    }

    private Optional<Contracts> findContractInScope(UUID contractId) {
        UUID orgId = TenantContext.require();
        return contractsRepository.findByIdAndOrganization_Id(contractId, orgId);
    }

    private Users getAuthenticatedUser() {
        String username = AuthenticatedUserUtils.getUsernameOrNull();
        return usersRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MSG));
    }
}
