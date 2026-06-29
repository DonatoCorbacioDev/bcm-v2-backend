package com.donatodev.bcm_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.MlResultCache;

@Repository
public interface MlResultCacheRepository extends JpaRepository<MlResultCache, Long> {

    Optional<MlResultCache> findByOrgIdAndCacheKey(Long orgId, String cacheKey);
}
