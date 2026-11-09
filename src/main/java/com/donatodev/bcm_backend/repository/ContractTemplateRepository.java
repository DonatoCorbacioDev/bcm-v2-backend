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

import com.donatodev.bcm_backend.entity.ContractTemplate;

@Repository
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, UUID> {

    List<ContractTemplate> findAllByOrgIdOrderByCreatedAtDesc(UUID orgId);

    Optional<ContractTemplate> findByIdAndOrgId(UUID id, UUID orgId);
}
