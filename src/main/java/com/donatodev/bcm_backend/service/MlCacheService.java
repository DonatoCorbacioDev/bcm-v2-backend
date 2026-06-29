package com.donatodev.bcm_backend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.entity.MlResultCache;
import com.donatodev.bcm_backend.repository.MlResultCacheRepository;

@Service
public class MlCacheService {

    static final Duration CACHE_TTL = Duration.ofHours(1);

    private final MlResultCacheRepository repository;

    public MlCacheService(MlResultCacheRepository repository) {
        this.repository = repository;
    }

    public Optional<String> get(Long orgId, String cacheKey) {
        if (orgId == null) return Optional.empty();
        return repository.findByOrgIdAndCacheKey(orgId, cacheKey)
                .filter(e -> e.getComputedAt().isAfter(LocalDateTime.now(ZoneId.systemDefault()).minus(CACHE_TTL)))
                .map(MlResultCache::getJsonResult);
    }

    @Transactional
    public void put(Long orgId, String cacheKey, String jsonResult) {
        if (orgId == null || jsonResult == null) return;
        MlResultCache entry = repository.findByOrgIdAndCacheKey(orgId, cacheKey)
                .orElse(MlResultCache.builder().orgId(orgId).cacheKey(cacheKey).build());
        entry.setJsonResult(jsonResult);
        entry.setComputedAt(LocalDateTime.now(ZoneId.systemDefault()));
        repository.save(entry);
    }
}
