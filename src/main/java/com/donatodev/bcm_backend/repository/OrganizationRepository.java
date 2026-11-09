/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.Organization;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    /**
     * The first organization ever created (oldest creation date, not lowest id:
     * ids carry no order once they are UUIDs, ADR-0009). Used by
     * {@link com.donatodev.bcm_backend.service.DevDataSeeder} to find *an*
     * organization to attach a demo account to without assuming its slug is
     * still "default" — V7 seeds it with that slug, but it's an ordinary,
     * renamable organization from then on, and demo/dev databases in
     * particular tend to get renamed to something more realistic.
     */
    Optional<Organization> findFirstByOrderByCreatedAtAsc();
}
