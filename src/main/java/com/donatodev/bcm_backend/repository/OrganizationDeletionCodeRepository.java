/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.donatodev.bcm_backend.entity.OrganizationDeletionCode;

public interface OrganizationDeletionCodeRepository extends JpaRepository<OrganizationDeletionCode, Long> {

    Optional<OrganizationDeletionCode> findByOrganizationId(Long organizationId);
}
