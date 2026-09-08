/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Nightly job that flags high-risk contracts for a notification. Goes through
 * {@link MlProxyService} (like {@link MlCacheRefresher}) rather than calling
 * FastAPI directly, so the internal API key and org_id are always attached —
 * a raw RestTemplate call here would silently 401 in any environment where
 * ML_INTERNAL_API_KEY is set (i.e. every real deployment).
 */
@Service
public class RiskScoreRefresher {

    private static final Logger logger = LoggerFactory.getLogger(RiskScoreRefresher.class);
    private static final String CRLF_REGEX = "[\r\n]";
    private static final double HIGH_RISK_THRESHOLD = 0.7;

    private final MlProxyService mlProxyService;
    private final ObjectMapper objectMapper;
    private final OrganizationRepository organizationRepository;
    private final ContractsRepository contractsRepository;
    private final AgentNotificationService agentNotificationService;

    public RiskScoreRefresher(
            MlProxyService mlProxyService,
            ObjectMapper objectMapper,
            OrganizationRepository organizationRepository,
            ContractsRepository contractsRepository,
            AgentNotificationService agentNotificationService) {
        this.mlProxyService = mlProxyService;
        this.objectMapper = objectMapper;
        this.organizationRepository = organizationRepository;
        this.contractsRepository = contractsRepository;
        this.agentNotificationService = agentNotificationService;
    }

    @Scheduled(cron = "0 30 6 * * *")
    public void refreshRiskScores() {
        List<Long> orgIds = organizationRepository.findAll().stream().map(org -> org.getId()).toList();
        logger.info("Starting risk score refresh for {} organization(s)...", orgIds.size());

        int notified = 0;
        for (Long orgId : orgIds) {
            notified += refreshForOrg(orgId);
        }

        logger.info("Risk score refresh completed. {} high-risk notifications created.", notified);
    }

    private int refreshForOrg(Long orgId) {
        try {
            ResponseEntity<String> response = mlProxyService.fetchRiskScoresRaw(orgId);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return 0;
            }

            RiskScoreEntry[] scores = objectMapper.readValue(response.getBody(), RiskScoreEntry[].class);
            int notified = 0;
            for (RiskScoreEntry entry : scores) {
                if (entry.riskScore() > HIGH_RISK_THRESHOLD && processHighRiskEntry(entry, orgId)) {
                    notified++;
                }
            }
            return notified;
        } catch (Exception e) {
            logger.warn("Risk score refresh failed for org {}: {}", orgId, safeMessage(e));
            return 0;
        }
    }

    private boolean processHighRiskEntry(RiskScoreEntry entry, Long orgId) {
        try {
            contractsRepository.findByIdAndOrganization_Id(entry.contractId(), orgId).ifPresent(contract ->
                    agentNotificationService.notifyHighRisk(contract, entry.riskScore()));
            return true;
        } catch (Exception e) {
            logger.error("Failed to process risk score for contract {}: {}", entry.contractId(), safeMessage(e));
            return false;
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? null : message.replaceAll(CRLF_REGEX, "_");
    }

    record RiskScoreEntry(Long contractId, double riskScore) {}
}
