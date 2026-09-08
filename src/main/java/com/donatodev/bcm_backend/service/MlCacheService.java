/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.entity.MlResultCache;
import com.donatodev.bcm_backend.repository.MlResultCacheRepository;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class MlCacheService {

    private static final Logger logger = LoggerFactory.getLogger(MlCacheService.class);

    static final Duration CACHE_TTL = Duration.ofHours(1);

    private final MlResultCacheRepository repository;
    private final MeterRegistry meterRegistry;

    public MlCacheService(MlResultCacheRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    public Optional<String> get(Long orgId, String cacheKey) {
        if (orgId == null) return Optional.empty();
        Optional<String> cached = repository.findByOrgIdAndCacheKey(orgId, cacheKey)
                .filter(e -> e.getComputedAt().isAfter(LocalDateTime.now(ZoneId.systemDefault()).minus(CACHE_TTL)))
                .map(MlResultCache::getJsonResult);
        meterRegistry.counter("bcm.ml.cache.result", "outcome", cached.isPresent() ? "hit" : "miss").increment();
        return cached;
    }

    /**
     * Find-then-save is not atomic: two concurrent misses for the same
     * (orgId, cacheKey) can both build a new entry and both call save(),
     * so the second hits the uq_ml_cache unique constraint. That's not a
     * real conflict — the other request already cached an equivalent, just
     * as fresh result — so it's swallowed here instead of propagating.
     * Callers (e.g. MlProxyService.getForecast) call put() inline with the
     * response they're about to return; letting this throw would turn an
     * already-successful computation into a failed request for the caller.
     */
    @Transactional
    public void put(Long orgId, String cacheKey, String jsonResult) {
        if (orgId == null || jsonResult == null) return;
        MlResultCache entry = repository.findByOrgIdAndCacheKey(orgId, cacheKey)
                .orElse(MlResultCache.builder().orgId(orgId).cacheKey(cacheKey).build());
        entry.setJsonResult(jsonResult);
        entry.setComputedAt(LocalDateTime.now(ZoneId.systemDefault()));
        try {
            repository.save(entry);
        } catch (DataIntegrityViolationException e) {
            logger.debug("Lost a concurrent cache write race for org {} key {}, ignoring", orgId, cacheKey);
        }
    }

    /**
     * Evicts every cached ML result (forecast, anomalies, any horizon) for
     * one organization, without enumerating individual cache keys. Callers
     * must invoke this only after the underlying financial data write has
     * already succeeded.
     * <p>
     * {@code @Transactional} is required here: the derived
     * {@code deleteByOrgId} query needs an active EntityManager transaction
     * to remove rows, and none of this method's callers (e.g.
     * {@code FinancialValueService}) open one themselves.
     */
    @Transactional
    public void evictAllForOrg(Long orgId) {
        if (orgId == null) return;
        repository.deleteByOrgId(orgId);
    }
}
