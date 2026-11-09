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

import com.donatodev.bcm_backend.entity.Counterparty;

@Repository
public interface CounterpartiesRepository extends JpaRepository<Counterparty, UUID> {

    List<Counterparty> findAllByOrganizationId(UUID organizationId);

    /**
     * Finds a counterparty by ID, scoped to the given organization. Used to
     * prevent cross-tenant access to counterparties by ID.
     */
    Optional<Counterparty> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Case-insensitive exact-name lookup within an organization, used both by
     * {@code ContractImportService} (resolve-or-create on Excel import) and by
     * anything else that needs to avoid creating duplicate counterparties for
     * the same company under slightly different casing.
     */
    Optional<Counterparty> findByNameIgnoreCaseAndOrganizationId(String name, UUID organizationId);
}
