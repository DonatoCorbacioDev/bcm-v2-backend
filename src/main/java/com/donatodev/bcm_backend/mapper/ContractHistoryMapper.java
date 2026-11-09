/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractHistoryDTO;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Mapper class responsible for converting between {@link ContractHistory} entities
 * and {@link ContractHistoryDTO} data transfer objects.
 * <p>
 * Used to decouple the data access layer from the API layer, ensuring clean
 * and controlled data transformations for contract history records.
 */
@Component
public class ContractHistoryMapper {

	private final ContractsRepository contractsRepository;
    private final UsersRepository usersRepository;

    public ContractHistoryMapper(ContractsRepository contractsRepository, UsersRepository usersRepository) {
        this.contractsRepository = contractsRepository;
        this.usersRepository = usersRepository;
    }

    /**
     * Converts a {@link ContractHistory} entity to a {@link ContractHistoryDTO}.
     *
     * @param history the contract history entity
     * @return the corresponding DTO
     */
    public ContractHistoryDTO toDTO(ContractHistory history) {
        Users author = history.getModifiedBy();
        return new ContractHistoryDTO(
                history.getId(),
                history.getContract().getId(),
                author != null ? author.getId() : null,
                history.getModificationDate(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                displayName(author)
        );
    }

    /** The person's name when the user has a manager profile, else the username. */
    private static String displayName(Users author) {
        if (author == null) {
            return null;
        }
        Managers manager = author.getManager();
        if (manager != null) {
            String name = ((manager.getFirstName() == null ? "" : manager.getFirstName()) + " "
                    + (manager.getLastName() == null ? "" : manager.getLastName())).trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return author.getUsername();
    }

    /**
     * Converts a {@link ContractHistoryDTO} to a {@link ContractHistory} entity.
     * <p>
     * This method also retrieves related entities (contract, user) from their repositories.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     * @throws RuntimeException if the referenced contract or user is not found
     */
    public ContractHistory toEntity(ContractHistoryDTO dto) {
        // Tenant-scoped: an admin must not write history onto another
        // organization's contract, nor sign it with another tenant's user.
        Long orgId = TenantContext.require();
        Contracts contract = contractsRepository.findByIdAndOrganization_Id(dto.contractId(), orgId)
                .orElseThrow(() -> new ContractNotFoundException("Contract not found"));
        Users modifiedBy = null;
        if (dto.modifiedById() != null) {
            modifiedBy = usersRepository.findByIdAndOrganizationId(dto.modifiedById(), orgId)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
        }
        return ContractHistory.builder()
                .id(dto.id())
                .contract(contract)
                .modifiedBy(modifiedBy)
                .modificationDate(dto.modificationDate() != null ? dto.modificationDate() : java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))
                .previousStatus(dto.previousStatus())
                .newStatus(dto.newStatus())
                .build();
    }
}
