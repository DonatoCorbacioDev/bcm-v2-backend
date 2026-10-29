/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.ContractDocument;

@Repository
public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Long> {

    List<ContractDocument> findByContractIdOrderByUploadedAtDesc(Long contractId);

    Optional<ContractDocument> findByIdAndContractId(Long id, Long contractId);

    List<ContractDocument> findByOrgIdAndEmbeddingIsNotNull(Long orgId);

    List<ContractDocument> findByVersionGroupIdOrderByVersionNumberDesc(Long versionGroupId);

    // Scoped through the contract rather than the row's own org_id column,
    // which older rows may lack: the contract is always set and always knows its org.
    List<ContractDocument> findAllByContract_Organization_Id(Long organizationId);
}
