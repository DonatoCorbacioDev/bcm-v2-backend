package com.donatodev.bcm_backend.service;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.service.RiskScoreRefresher.RiskScoreEntry;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class RiskScoreRefresherTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ContractsRepository contractsRepository;
    @Mock private AgentNotificationService agentNotificationService;

    @InjectMocks private RiskScoreRefresher riskScoreRefresher;

    private static final String FASTAPI_URL = "http://localhost:8000";

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: RiskScoreRefresher")
    @SuppressWarnings("unused")
    class VerifyRiskScoreRefresher {

        @Test
        @Order(1)
        @DisplayName("Should notify for contracts with riskScore above 0.7")
        void shouldNotifyHighRiskContracts() {
            ReflectionTestUtils.setField(riskScoreRefresher, "fastApiUrl", FASTAPI_URL);

            RiskScoreEntry highRisk = new RiskScoreEntry(1L, 0.85);
            RiskScoreEntry lowRisk  = new RiskScoreEntry(2L, 0.3);
            RiskScoreEntry[] scores = { highRisk, lowRisk };

            Contracts contract = Contracts.builder().id(1L).contractNumber("CNT-HIGH").build();

            when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn(scores);
            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));

            riskScoreRefresher.refreshRiskScores();

            verify(contractsRepository).findById(1L);
            verify(contractsRepository, never()).findById(2L);
            verify(agentNotificationService).notifyHighRisk(contract, 0.85);
        }

        @Test
        @Order(2)
        @DisplayName("Should not notify for contracts with riskScore at or below 0.7")
        void shouldNotNotifyLowRiskContracts() {
            ReflectionTestUtils.setField(riskScoreRefresher, "fastApiUrl", FASTAPI_URL);

            RiskScoreEntry[] scores = {
                new RiskScoreEntry(1L, 0.7),
                new RiskScoreEntry(2L, 0.5)
            };

            when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn(scores);

            riskScoreRefresher.refreshRiskScores();

            verify(contractsRepository, never()).findById(any());
            verify(agentNotificationService, never()).notifyHighRisk(any(), anyDouble());
        }

        @Test
        @Order(3)
        @DisplayName("Should handle null API response gracefully")
        void shouldHandleNullResponse() {
            ReflectionTestUtils.setField(riskScoreRefresher, "fastApiUrl", FASTAPI_URL);

            when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn(null);

            assertDoesNotThrow(() -> riskScoreRefresher.refreshRiskScores());

            verify(agentNotificationService, never()).notifyHighRisk(any(), anyDouble());
        }

        @Test
        @Order(4)
        @DisplayName("Should handle FastAPI offline gracefully")
        void shouldHandleFastApiOffline() {
            ReflectionTestUtils.setField(riskScoreRefresher, "fastApiUrl", FASTAPI_URL);

            when(restTemplate.getForObject(anyString(), any(Class.class)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            assertDoesNotThrow(() -> riskScoreRefresher.refreshRiskScores());

            verify(contractsRepository, never()).findById(any());
            verify(agentNotificationService, never()).notifyHighRisk(any(), anyDouble());
        }

        @Test
        @Order(5)
        @DisplayName("Should continue processing when one contract lookup throws")
        void shouldContinueWhenContractLookupFails() {
            ReflectionTestUtils.setField(riskScoreRefresher, "fastApiUrl", FASTAPI_URL);

            RiskScoreEntry[] scores = {
                new RiskScoreEntry(1L, 0.9),
                new RiskScoreEntry(2L, 0.8)
            };

            Contracts contract2 = Contracts.builder().id(2L).contractNumber("CNT-002").build();

            when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn(scores);
            when(contractsRepository.findById(1L)).thenThrow(new RuntimeException("DB error"));
            when(contractsRepository.findById(2L)).thenReturn(Optional.of(contract2));

            assertDoesNotThrow(() -> riskScoreRefresher.refreshRiskScores());

            verify(agentNotificationService).notifyHighRisk(contract2, 0.8);
        }

        @Test
        @Order(6)
        @DisplayName("Should skip notification when contract not found in DB")
        void shouldSkipWhenContractNotFound() {
            ReflectionTestUtils.setField(riskScoreRefresher, "fastApiUrl", FASTAPI_URL);

            RiskScoreEntry[] scores = { new RiskScoreEntry(99L, 0.95) };

            when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn(scores);
            when(contractsRepository.findById(99L)).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> riskScoreRefresher.refreshRiskScores());

            verify(agentNotificationService, never()).notifyHighRisk(any(), anyDouble());
        }
    }
}
