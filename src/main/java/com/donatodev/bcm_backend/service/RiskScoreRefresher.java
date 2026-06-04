package com.donatodev.bcm_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.donatodev.bcm_backend.repository.ContractsRepository;

@Service
public class RiskScoreRefresher {

    private static final Logger logger = LoggerFactory.getLogger(RiskScoreRefresher.class);

    @Value("${ml.fastapi.url:http://localhost:8000}")
    private String fastApiUrl;

    private final RestTemplate restTemplate;
    private final ContractsRepository contractsRepository;
    private final AgentNotificationService agentNotificationService;

    public RiskScoreRefresher(RestTemplate restTemplate, ContractsRepository contractsRepository,
            AgentNotificationService agentNotificationService) {
        this.restTemplate = restTemplate;
        this.contractsRepository = contractsRepository;
        this.agentNotificationService = agentNotificationService;
    }

    @Scheduled(cron = "0 30 6 * * *")
    public void refreshRiskScores() {
        logger.info("Starting risk score refresh from FastAPI...");
        try {
            RiskScoreEntry[] scores = restTemplate.getForObject(fastApiUrl + "/risk-scores", RiskScoreEntry[].class);
            if (scores == null) {
                logger.warn("Risk score API returned null response");
                return;
            }

            int notified = 0;
            for (RiskScoreEntry entry : scores) {
                if (entry.riskScore() > 0.7) {
                    try {
                        contractsRepository.findById(entry.contractId()).ifPresent(contract ->
                                agentNotificationService.notifyHighRisk(contract, entry.riskScore()));
                        notified++;
                    } catch (Exception e) {
                        logger.error("Failed to process risk score for contract {}: {}", entry.contractId(), e.getMessage());
                    }
                }
            }

            logger.info("Risk score refresh completed. {} high-risk notifications created.", notified);
        } catch (Exception e) {
            logger.warn("FastAPI risk score service unavailable, skipping refresh: {}", e.getMessage());
        }
    }

    record RiskScoreEntry(Long contractId, double riskScore) {}
}
