package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.NotificationType;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class AgentNotificationServiceTest {

    @Mock private NotificationService notificationService;
    @Mock private UsersRepository usersRepository;

    @InjectMocks private AgentNotificationService agentNotificationService;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: AgentNotificationService")
    @SuppressWarnings("unused")
    class VerifyAgentNotificationService {

        @Test
        @Order(1)
        @DisplayName("Should create WARNING notification for expiring contract with manager")
        void shouldNotifyExpiringContractWhenManagerHasUser() {
            Organization org = Organization.builder().id(10L).name("TestOrg").build();
            Managers manager = new Managers();
            manager.setId(1L);
            manager.setEmail("manager@test.com");

            Contracts contract = Contracts.builder()
                    .id(1L).contractNumber("CNT-001").customerName("Client A")
                    .status(ContractStatus.ACTIVE)
                    .startDate(LocalDate.now().minusMonths(6))
                    .endDate(LocalDate.now().plusDays(20))
                    .manager(manager).build();

            Users user = Users.builder().id(5L).username("mgr").organization(org).build();
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com"))
                    .thenReturn(Optional.of(user));

            agentNotificationService.notifyExpiringContract(contract);

            verify(notificationService).createForUser(eq(5L), eq(10L), anyString(), anyString(),
                    eq(NotificationType.WARNING));
        }

        @Test
        @Order(2)
        @DisplayName("Should skip notification when contract has no manager")
        void shouldSkipWhenContractHasNoManager() {
            Contracts contract = Contracts.builder()
                    .id(1L).contractNumber("CNT-002")
                    .endDate(LocalDate.now().plusDays(15))
                    .manager(null).build();

            agentNotificationService.notifyExpiringContract(contract);

            verify(notificationService, never()).createForUser(anyLong(), anyLong(), anyString(), anyString(), any());
        }

        @Test
        @Order(3)
        @DisplayName("Should skip notification when manager has no email")
        void shouldSkipWhenManagerHasNoEmail() {
            Managers manager = new Managers();
            manager.setId(1L);
            manager.setEmail(null);

            Contracts contract = Contracts.builder()
                    .id(1L).contractNumber("CNT-003")
                    .endDate(LocalDate.now().plusDays(10))
                    .manager(manager).build();

            agentNotificationService.notifyExpiringContract(contract);

            verify(notificationService, never()).createForUser(anyLong(), anyLong(), anyString(), anyString(), any());
        }

        @Test
        @Order(4)
        @DisplayName("Should skip notification when user has no organization")
        void shouldSkipWhenUserHasNoOrganization() {
            Managers manager = new Managers();
            manager.setId(1L);
            manager.setEmail("noorg@test.com");

            Contracts contract = Contracts.builder()
                    .id(1L).contractNumber("CNT-004")
                    .endDate(LocalDate.now().plusDays(10))
                    .manager(manager).build();

            Users user = Users.builder().id(5L).username("orphan").organization(null).build();
            when(usersRepository.findByManagerEmailIgnoreCase("noorg@test.com"))
                    .thenReturn(Optional.of(user));

            agentNotificationService.notifyExpiringContract(contract);

            verify(notificationService, never()).createForUser(anyLong(), anyLong(), anyString(), anyString(), any());
        }

        @Test
        @Order(5)
        @DisplayName("Should create ERROR notification for high-risk contract")
        void shouldNotifyHighRisk() {
            Organization org = Organization.builder().id(10L).name("TestOrg").build();
            Managers manager = new Managers();
            manager.setId(1L);
            manager.setEmail("manager@test.com");

            Contracts contract = Contracts.builder()
                    .id(1L).contractNumber("CNT-RISK-001")
                    .customerName("Risky Client")
                    .endDate(LocalDate.now().plusMonths(3))
                    .manager(manager).build();

            Users user = Users.builder().id(5L).username("mgr").organization(org).build();
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com"))
                    .thenReturn(Optional.of(user));

            agentNotificationService.notifyHighRisk(contract, 0.85);

            verify(notificationService).createForUser(eq(5L), eq(10L), anyString(), anyString(),
                    eq(NotificationType.ERROR));
        }

        @Test
        @Order(6)
        @DisplayName("Should skip high-risk notification when contract has no manager")
        void shouldSkipHighRiskWhenNoManager() {
            Contracts contract = Contracts.builder()
                    .id(1L).contractNumber("CNT-RISK-002").manager(null).build();

            agentNotificationService.notifyHighRisk(contract, 0.9);

            verify(notificationService, never()).createForUser(anyLong(), anyLong(), anyString(), anyString(), any());
        }

        @Test
        @Order(7)
        @DisplayName("Should create WARNING notification for anomaly detected")
        void shouldNotifyAnomalyDetected() {
            Organization org = Organization.builder().id(10L).name("TestOrg").build();
            Managers manager = new Managers();
            manager.setId(1L);
            manager.setEmail("manager@test.com");

            Contracts contract = Contracts.builder()
                    .id(1L).contractNumber("CNT-ANOM-001")
                    .manager(manager).build();

            Users user = Users.builder().id(5L).username("mgr").organization(org).build();
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com"))
                    .thenReturn(Optional.of(user));

            assertDoesNotThrow(() ->
                    agentNotificationService.notifyAnomalyDetected(contract, "Unusual value detected"));

            verify(notificationService).createForUser(eq(5L), eq(10L), anyString(), anyString(),
                    eq(NotificationType.WARNING));
        }
    }
}
