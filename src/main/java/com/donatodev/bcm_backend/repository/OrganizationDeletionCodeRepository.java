/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.donatodev.bcm_backend.entity.OrganizationDeletionCode;

import jakarta.persistence.LockModeType;

public interface OrganizationDeletionCodeRepository extends JpaRepository<OrganizationDeletionCode, UUID> {

    Optional<OrganizationDeletionCode> findByOrganizationId(UUID organizationId);

    /**
     * The organization's pending code, its row locked until the transaction
     * ends: concurrent confirmations wait for one another, so every wrong code
     * is counted and the fifth drops it (security audit 2026-11-24).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM OrganizationDeletionCode c WHERE c.organizationId = :organizationId")
    Optional<OrganizationDeletionCode> findForUpdateByOrganizationId(@Param("organizationId") UUID organizationId);
}
