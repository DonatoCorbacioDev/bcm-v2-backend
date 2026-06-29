package com.donatodev.bcm_backend.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.repository.OrganizationRepository;

@Component
public class MlCacheRefresher {

    private static final Logger logger = LoggerFactory.getLogger(MlCacheRefresher.class);
    private static final int[] FORECAST_HORIZONS = {3, 6};

    private final OrganizationRepository organizationRepository;
    private final MlProxyService mlProxyService;
    private final MlCacheService mlCacheService;

    public MlCacheRefresher(OrganizationRepository organizationRepository,
                            MlProxyService mlProxyService,
                            MlCacheService mlCacheService) {
        this.organizationRepository = organizationRepository;
        this.mlProxyService = mlProxyService;
        this.mlCacheService = mlCacheService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void refreshAll() {
        List<Long> orgIds = organizationRepository.findAll()
                .stream()
                .map(org -> org.getId())
                .toList();
        logger.info("ML cache refresh started for {} organization(s)", orgIds.size());
        int refreshed = 0;
        for (Long orgId : orgIds) {
            if (refreshForOrg(orgId)) refreshed++;
        }
        logger.info("ML cache refresh complete: {}/{} organizations updated", refreshed, orgIds.size());
    }

    boolean refreshForOrg(Long orgId) {
        try {
            for (int months : FORECAST_HORIZONS) {
                ResponseEntity<String> r = mlProxyService.fetchForecastRaw(months, orgId);
                if (r.getStatusCode().is2xxSuccessful() && r.getBody() != null) {
                    mlCacheService.put(orgId, "FORECAST_" + months, r.getBody());
                }
            }
            ResponseEntity<String> r = mlProxyService.fetchAnomaliesRaw(orgId);
            if (r.getStatusCode().is2xxSuccessful() && r.getBody() != null) {
                mlCacheService.put(orgId, "ANOMALIES", r.getBody());
            }
            return true;
        } catch (Exception e) {
            logger.warn("ML cache refresh failed for org {}: {}", orgId, e.getMessage());
            return false;
        }
    }
}
