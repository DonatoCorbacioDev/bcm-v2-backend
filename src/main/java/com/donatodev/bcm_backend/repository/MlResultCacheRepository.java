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

import com.donatodev.bcm_backend.entity.MlResultCache;

@Repository
public interface MlResultCacheRepository extends JpaRepository<MlResultCache, UUID> {

    Optional<MlResultCache> findByOrgIdAndCacheKey(UUID orgId, String cacheKey);

    void deleteByOrgId(UUID orgId);

    List<MlResultCache> findAllByOrgId(UUID orgId);
}
