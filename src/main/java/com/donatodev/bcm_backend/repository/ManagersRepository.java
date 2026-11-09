/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.Managers;

@Repository
public interface ManagersRepository extends JpaRepository<Managers, UUID> {

    Managers findByEmail(String email);

    boolean existsByEmail(String email);

    Page<Managers> findByOrganizationId(UUID organizationId, Pageable pageable);

    /**
     * Finds all managers belonging to the given organization, unpaged. Used
     * by bulk contract import to resolve manager names against the full
     * roster of an organization in one query.
     *
     * @param organizationId the organization ID
     * @return all managers belonging to the organization
     */
    List<Managers> findAllByOrganizationId(UUID organizationId);

    /**
     * Finds a manager by ID, scoped to the given organization. Used to
     * prevent cross-tenant access to managers by ID.
     *
     * @param id the manager ID
     * @param orgId the organization ID
     * @return an {@link Optional} containing the manager if it exists and
     * belongs to the given organization
     */
    Optional<Managers> findByIdAndOrganizationId(UUID id, UUID orgId);

    Page<Managers> findByOrganizationIdAndFirstNameContainingIgnoreCaseOrOrganizationIdAndLastNameContainingIgnoreCaseOrOrganizationIdAndEmailContainingIgnoreCase(
            UUID orgId1, String first,
            UUID orgId2, String last,
            UUID orgId3, String email,
            Pageable pageable);

    Page<Managers> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String first, String last, String email, Pageable pageable);
}
