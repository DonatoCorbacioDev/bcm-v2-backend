/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import com.donatodev.bcm_backend.entity.ContractManager;
import com.donatodev.bcm_backend.entity.ContractManagerId;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for accessing {@link ContractManager} entities,
 * which represent the relationship between contracts and managers.
 * <p>
 * Uses {@link ContractManagerId} as a composite primary key.
 */
@Repository
public interface ContractManagerRepository extends JpaRepository<ContractManager, ContractManagerId> {
	
    List<ContractManager> findAllByContract_Organization_Id(UUID organizationId);
}
