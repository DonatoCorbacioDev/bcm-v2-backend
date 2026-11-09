/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.donatodev.bcm_backend.entity.TermsAcceptance;

public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, UUID> {

    List<TermsAcceptance> findByOrganizationIdOrderByAcceptedAtDesc(UUID organizationId);
}
