package com.donatodev.bcm_backend.service;

import java.util.List;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class RiskScoreRefresherTest {

    @Mock private MlProxyService mlProxyService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private ContractsRepository contractsRepository;
    @Mock private AgentNotificationService agentNotificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RiskScoreRefresher riskScoreRefresher;

    private static final Long ORG_ID = 1L;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        riskScoreRefresher = new RiskScoreRefresher(
                mlProxyService, objectMapper, organizationRepository, contractsRepository, agentNotificationService);
    }

    private Organization org(Long id) {
        Organization o = new Organization();
        o.setId(id);
        return o;
    }

    private void oneOrg() {
        when(organizationRepository.findAll()).thenReturn(List.of(org(ORG_ID)));
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: RiskScoreRefresher")
    @SuppressWarnings("unused")
    class VerifyRiskScoreRefresher {

        @Test
        @Order(1)
        @DisplayName("Should notify for contracts with riskScore above 0.7")
        void shouldNotifyHighRiskContracts() {
            oneOrg();
            String json = "[{\"contractId\":1,\"riskScore\":0.85},{\"contractId\":2,\"riskScore\":0.3}]";
            Contracts contract = Contracts.builder().id(1L).contractNumber("CNT-HIGH").build();

            when(mlProxyService.fetchRiskScoresRaw(ORG_ID)).thenReturn(ResponseEntity.ok(json));
            when(contractsRepository.findByIdAndOrganization_Id(1L, ORG_ID)).thenReturn(Optional.of(contract));

            riskScoreRefresher.refreshRiskScores();

            verify(contractsRepository).findByIdAndOrganization_Id(1L, ORG_ID);
            verify(contractsRepository, never()).findByIdAndOrganization_Id(eq(2L), any());
            verify(agentNotificationService).notifyHighRisk(contract, 0.85);
        }

        @Test
        @Order(2)
        @DisplayName("Should not notify for contracts with riskScore at or below 0.7")
        void shouldNotNotifyLowRiskContracts() {
            oneOrg();
            String json = "[{\"contractId\":1,\"riskScore\":0.7},{\"contractId\":2,\"riskScore\":0.5}]";
            when(mlProxyService.fetchRiskScoresRaw(ORG_ID)).thenReturn(ResponseEntity.ok(json));

            riskScoreRefresher.refreshRiskScores();

            verify(contractsRepository, never()).findByIdAndOrganization_Id(any(), any());
            verify(agentNotificationService, never()).notifyHighRisk(any(), anyDouble());
        }

        @Test
        @Order(3)
        @DisplayName("Should handle a null-body API response gracefully")
        void shouldHandleNullResponse() {
            oneOrg();
            when(mlProxyService.fetchRiskScoresRaw(ORG_ID)).thenReturn(ResponseEntity.ok().body(null));

            assertDoesNotThrow(() -> riskScoreRefresher.refreshRiskScores());

            verify(agentNotificationService, never()).notifyHighRisk(any(), anyDouble());
        }

        @Test
        @Order(4)
        @DisplayName("Should handle FastAPI offline (503) gracefully")
        void shouldHandleFastApiOffline() {
            oneOrg();
            when(mlProxyService.fetchRiskScoresRaw(ORG_ID))
                    .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());

            assertDoesNotThrow(() -> riskScoreRefresher.refreshRiskScores());

            verify(contractsRepository, never()).findByIdAndOrganization_Id(any(), any());
            verify(agentNotificationService, never()).notifyHighRisk(any(), anyDouble());
        }

        @Test
        @Order(5)
        @DisplayName("Should continue processing when one contract lookup throws")
        void shouldContinueWhenContractLookupFails() {
            oneOrg();
            String json = "[{\"contractId\":1,\"riskScore\":0.9},{\"contractId\":2,\"riskScore\":0.8}]";
            Contracts contract2 = Contracts.builder().id(2L).contractNumber("CNT-002").build();

            when(mlProxyService.fetchRiskScoresRaw(ORG_ID)).thenReturn(ResponseEntity.ok(json));
            when(contractsRepository.findByIdAndOrganization_Id(1L, ORG_ID)).thenThrow(new RuntimeException("DB error"));
            when(contractsRepository.findByIdAndOrganization_Id(2L, ORG_ID)).thenReturn(Optional.of(contract2));

            assertDoesNotThrow(() -> riskScoreRefresher.refreshRiskScores());

            verify(agentNotificationService).notifyHighRisk(contract2, 0.8);
        }

        @Test
        @Order(6)
        @DisplayName("Should continue processing when a contract lookup throws with no message")
        void shouldContinueWhenContractLookupFailsWithNullMessage() {
            oneOrg();
            String json = "[{\"contractId\":1,\"riskScore\":0.9},{\"contractId\":2,\"riskScore\":0.8}]";
            Contracts contract2 = Contracts.builder().id(2L).contractNumber("CNT-002").build();

            when(mlProxyService.fetchRiskScoresRaw(ORG_ID)).thenReturn(ResponseEntity.ok(json));
            when(contractsRepository.findByIdAndOrganization_Id(1L, ORG_ID)).thenThrow(new RuntimeException());
            when(contractsRepository.findByIdAndOrganization_Id(2L, ORG_ID)).thenReturn(Optional.of(contract2));

            assertDoesNotThrow(() -> riskScoreRefresher.refreshRiskScores());

            verify(agentNotificationService).notifyHighRisk(contract2, 0.8);
        }

        @Test
        @Order(7)
        @DisplayName("Should skip notification when contract not found in the org")
        void shouldSkipWhenContractNotFound() {
            oneOrg();
            String json = "[{\"contractId\":99,\"riskScore\":0.95}]";

            when(mlProxyService.fetchRiskScoresRaw(ORG_ID)).thenReturn(ResponseEntity.ok(json));
            when(contractsRepository.findByIdAndOrganization_Id(99L, ORG_ID)).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> riskScoreRefresher.refreshRiskScores());

            verify(agentNotificationService, never()).notifyHighRisk(any(), anyDouble());
        }

        @Test
        @Order(8)
        @DisplayName("Should continue refreshing remaining orgs when one fails")
        void shouldContinueWhenOneOrgFails() {
            when(organizationRepository.findAll()).thenReturn(List.of(org(1L), org(2L)));
            when(mlProxyService.fetchRiskScoresRaw(1L)).thenThrow(new RuntimeException("ML down"));
            String json = "[{\"contractId\":5,\"riskScore\":0.9}]";
            Contracts contract = Contracts.builder().id(5L).contractNumber("CNT-005").build();
            when(mlProxyService.fetchRiskScoresRaw(2L)).thenReturn(ResponseEntity.ok(json));
            when(contractsRepository.findByIdAndOrganization_Id(5L, 2L)).thenReturn(Optional.of(contract));

            assertDoesNotThrow(() -> riskScoreRefresher.refreshRiskScores());

            verify(agentNotificationService).notifyHighRisk(contract, 0.9);
        }

        @Test
        @Order(9)
        @DisplayName("Never queries a contract by an unscoped id across organizations")
        void neverLooksUpContractsUnscoped() {
            oneOrg();
            String json = "[{\"contractId\":1,\"riskScore\":0.9}]";
            when(mlProxyService.fetchRiskScoresRaw(ORG_ID)).thenReturn(ResponseEntity.ok(json));
            when(contractsRepository.findByIdAndOrganization_Id(1L, ORG_ID)).thenReturn(Optional.empty());

            riskScoreRefresher.refreshRiskScores();

            verify(contractsRepository, never()).findById(any());
        }
    }
}
