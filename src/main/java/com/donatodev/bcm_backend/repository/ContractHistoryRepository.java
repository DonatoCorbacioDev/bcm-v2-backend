/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.ContractHistory;

/**
 * Repository interface for accessing {@link ContractHistory} entities.
 * <p>
 * Provides standard CRUD operations and a method to retrieve history entries
 * related to a specific contract.
 */
@Repository
public interface ContractHistoryRepository extends JpaRepository<ContractHistory, UUID> {

    /**
     * Retrieves all contract history entries for a specific contract ID.
     *
     * @param contractId the ID of the contract
     * @return a list of {@link ContractHistory} records
     */
    List<ContractHistory> findByContractId(UUID contractId);

    List<ContractHistory> findByContractManagerId(UUID managerId);

    /**
     * Retrieves all contract history entries belonging to the given organization,
     * via the related contract's organization.
     *
     * @param organizationId the organization ID
     * @return a list of {@link ContractHistory} records
     */
    List<ContractHistory> findByContract_Organization_Id(UUID organizationId);

    /**
     * Retrieves a contract history entry by ID, scoped to the given organization
     * via the related contract's organization.
     *
     * @param id the history entry ID
     * @param organizationId the organization ID
     * @return the matching {@link ContractHistory}, if any
     */
    Optional<ContractHistory> findByIdAndContract_Organization_Id(UUID id, UUID organizationId);

    /**
     * Retrieves all contract history entries for a specific contract ID,
     * scoped to the given organization via the related contract's organization.
     *
     * @param contractId the contract ID
     * @param organizationId the organization ID
     * @return a list of {@link ContractHistory} records
     */
    List<ContractHistory> findByContractIdAndContract_Organization_Id(UUID contractId, UUID organizationId);
}
