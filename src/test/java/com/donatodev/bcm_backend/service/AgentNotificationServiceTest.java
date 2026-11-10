package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import java.time.LocalDate;
import java.time.Month;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
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
            Organization org = Organization.builder().id(uid(10)).name("TestOrg").build();
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail("manager@test.com");

            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-001")
                    .counterparty(Counterparty.builder().name("Client A").type(CounterpartyType.CUSTOMER).build())
                    .status(ContractStatus.ACTIVE)
                    .startDate(LocalDate.of(2026, Month.DECEMBER, 15))
                    .endDate(LocalDate.of(2027, Month.JULY, 5))
                    .manager(manager).build();

            Users user = Users.builder().id(uid(5)).username("mgr").organization(org).build();
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com"))
                    .thenReturn(Optional.of(user));

            agentNotificationService.notifyExpiringContract(contract);

            verify(notificationService).createForUser(eq(uid(5)), eq(uid(10)), anyString(), anyString(),
                    eq(NotificationType.WARNING));
        }

        @Test
        @Order(13)
        @DisplayName("Should use singular 'giorno' when the contract expires in exactly 1 day")
        void shouldNotifyExpiringContractWithSingularDayWord() {
            Organization org = Organization.builder().id(uid(10)).name("TestOrg").build();
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail("manager@test.com");

            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-005")
                    .counterparty(Counterparty.builder().name("Client A").type(CounterpartyType.CUSTOMER).build())
                    .status(ContractStatus.ACTIVE)
                    .endDate(LocalDate.now().plusDays(1))
                    .manager(manager).build();

            Users user = Users.builder().id(uid(5)).username("mgr").organization(org).build();
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com"))
                    .thenReturn(Optional.of(user));

            agentNotificationService.notifyExpiringContract(contract);

            verify(notificationService).createForUser(eq(uid(5)), eq(uid(10)), anyString(),
                    contains("scade tra 1 giorno ("), eq(NotificationType.WARNING));
        }

        @Test
        @Order(2)
        @DisplayName("Should skip notification when contract has no manager")
        void shouldSkipWhenContractHasNoManager() {
            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-002")
                    .endDate(LocalDate.of(2027, Month.JUNE, 30))
                    .manager(null).build();

            agentNotificationService.notifyExpiringContract(contract);

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @Order(3)
        @DisplayName("Should skip notification when manager has no email")
        void shouldSkipWhenManagerHasNoEmail() {
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail(null);

            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-003")
                    .endDate(LocalDate.of(2027, Month.JUNE, 25))
                    .manager(manager).build();

            agentNotificationService.notifyExpiringContract(contract);

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @Order(4)
        @DisplayName("Should skip notification when user has no organization")
        void shouldSkipWhenUserHasNoOrganization() {
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail("noorg@test.com");

            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-004")
                    .endDate(LocalDate.of(2027, Month.JUNE, 25))
                    .manager(manager).build();

            Users user = Users.builder().id(uid(5)).username("orphan").organization(null).build();
            when(usersRepository.findByManagerEmailIgnoreCase("noorg@test.com"))
                    .thenReturn(Optional.of(user));

            agentNotificationService.notifyExpiringContract(contract);

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @Order(5)
        @DisplayName("Should create ERROR notification for high-risk contract")
        void shouldNotifyHighRisk() {
            Organization org = Organization.builder().id(uid(10)).name("TestOrg").build();
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail("manager@test.com");

            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-RISK-001")
                    .counterparty(Counterparty.builder().name("Risky Client").type(CounterpartyType.CUSTOMER).build())
                    .endDate(LocalDate.of(2027, Month.SEPTEMBER, 15))
                    .manager(manager).build();

            Users user = Users.builder().id(uid(5)).username("mgr").organization(org).build();
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com"))
                    .thenReturn(Optional.of(user));

            agentNotificationService.notifyHighRisk(contract, 0.85);

            verify(notificationService).createForUser(eq(uid(5)), eq(uid(10)), anyString(), anyString(),
                    eq(NotificationType.ERROR));
        }

        @Test
        @Order(6)
        @DisplayName("Should skip high-risk notification when contract has no manager")
        void shouldSkipHighRiskWhenNoManager() {
            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-RISK-002").manager(null).build();

            agentNotificationService.notifyHighRisk(contract, 0.9);

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @Order(7)
        @DisplayName("Should create WARNING notification for anomaly detected")
        void shouldNotifyAnomalyDetected() {
            Organization org = Organization.builder().id(uid(10)).name("TestOrg").build();
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail("manager@test.com");

            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-ANOM-001")
                    .manager(manager).build();

            Users user = Users.builder().id(uid(5)).username("mgr").organization(org).build();
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com"))
                    .thenReturn(Optional.of(user));

            assertDoesNotThrow(() ->
                    agentNotificationService.notifyAnomalyDetected(contract, "Unusual value detected"));

            verify(notificationService).createForUser(eq(uid(5)), eq(uid(10)), anyString(), anyString(),
                    eq(NotificationType.WARNING));
        }

        @Test
        @Order(8)
        @DisplayName("Should skip high-risk notification when manager email is null")
        void shouldSkipHighRiskWhenManagerEmailIsNull() {
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail(null);

            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-RISK-003").manager(manager).build();

            agentNotificationService.notifyHighRisk(contract, 0.9);

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @Order(9)
        @DisplayName("Should skip high-risk notification when user has no organization")
        void shouldSkipHighRiskWhenUserHasNoOrganization() {
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail("noorg@risk.com");

            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-RISK-004")
                    .endDate(LocalDate.of(2027, Month.SEPTEMBER, 15))
                    .manager(manager).build();

            Users user = Users.builder().id(uid(5)).username("orphan").organization(null).build();
            when(usersRepository.findByManagerEmailIgnoreCase("noorg@risk.com"))
                    .thenReturn(Optional.of(user));

            agentNotificationService.notifyHighRisk(contract, 0.85);

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @Order(10)
        @DisplayName("Should skip anomaly notification when contract has no manager")
        void shouldSkipAnomalyWhenContractHasNoManager() {
            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-ANOM-002").manager(null).build();

            agentNotificationService.notifyAnomalyDetected(contract, "Anomaly");

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @Order(11)
        @DisplayName("Should skip anomaly notification when manager email is null")
        void shouldSkipAnomalyWhenManagerEmailIsNull() {
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail(null);

            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-ANOM-003").manager(manager).build();

            agentNotificationService.notifyAnomalyDetected(contract, "Anomaly");

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @Order(12)
        @DisplayName("Should skip anomaly notification when user has no organization")
        void shouldSkipAnomalyWhenUserHasNoOrganization() {
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail("noorg@anom.com");

            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-ANOM-004").manager(manager).build();

            Users user = Users.builder().id(uid(5)).username("orphan").organization(null).build();
            when(usersRepository.findByManagerEmailIgnoreCase("noorg@anom.com"))
                    .thenReturn(Optional.of(user));

            agentNotificationService.notifyAnomalyDetected(contract, "Anomaly");

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Unit Test: AgentNotificationService workflow notifications")
    @SuppressWarnings("unused")
    class VerifyWorkflowNotifications {

        @Test
        @DisplayName("Should skip submitted-for-review notification when contract has no organization")
        void skipsSubmittedForReviewWhenNoOrganization() {
            Contracts contract = Contracts.builder().id(uid(1)).contractNumber("CNT-WF-001").build();

            agentNotificationService.notifySubmittedForReview(contract);

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should notify each admin and approver once, de-duplicating a user who is both")
        void notifiesAdminsAndApproversDeduped() {
            Organization org = Organization.builder().id(uid(10)).name("TestOrg").build();
            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-WF-002")
                    .counterparty(Counterparty.builder().name("Client B").type(CounterpartyType.CUSTOMER).build())
                    .organization(org).build();

            Users adminAndApprover = Users.builder().id(uid(1)).username("admin1").build();
            Users approverOnly = Users.builder().id(uid(2)).username("approver1").build();
            when(usersRepository.findByOrganizationIdAndRoleRole(uid(10), "ADMIN"))
                    .thenReturn(java.util.List.of(adminAndApprover));
            when(usersRepository.findByOrganizationIdAndCanApproveContractsTrue(uid(10)))
                    .thenReturn(java.util.List.of(adminAndApprover, approverOnly));

            agentNotificationService.notifySubmittedForReview(contract);

            verify(notificationService).createForUser(eq(uid(1)), eq(uid(10)), anyString(), anyString(), eq(NotificationType.INFO));
            verify(notificationService).createForUser(eq(uid(2)), eq(uid(10)), anyString(), anyString(), eq(NotificationType.INFO));
        }

        @Test
        @DisplayName("Should notify the contract's manager when approved")
        void notifiesApproval() {
            Organization org = Organization.builder().id(uid(10)).name("TestOrg").build();
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail("manager@test.com");
            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-WF-003").manager(manager).build();

            Users user = Users.builder().id(uid(5)).username("mgr").organization(org).build();
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com")).thenReturn(Optional.of(user));

            agentNotificationService.notifyWorkflowApproved(contract);

            verify(notificationService).createForUser(eq(uid(5)), eq(uid(10)), anyString(), anyString(), eq(NotificationType.INFO));
        }

        @Test
        @DisplayName("Should skip approval notification when the contract has no manager")
        void skipsApprovalWhenNoManager() {
            Contracts contract = Contracts.builder().id(uid(1)).contractNumber("CNT-WF-004").manager(null).build();

            agentNotificationService.notifyWorkflowApproved(contract);

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should notify the contract's manager with the comment when rejected")
        void notifiesRejectionWithComment() {
            Organization org = Organization.builder().id(uid(10)).name("TestOrg").build();
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail("manager@test.com");
            Contracts contract = Contracts.builder()
                    .id(uid(1)).contractNumber("CNT-WF-005").manager(manager).build();

            Users user = Users.builder().id(uid(5)).username("mgr").organization(org).build();
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com")).thenReturn(Optional.of(user));

            agentNotificationService.notifyWorkflowRejected(contract, "Manca l'allegato");

            verify(notificationService).createForUser(eq(uid(5)), eq(uid(10)), anyString(),
                    contains("Manca l'allegato"), eq(NotificationType.WARNING));
        }

        @Test
        @DisplayName("Should skip rejection notification when the manager has no email")
        void skipsRejectionWhenManagerHasNoEmail() {
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail(null);
            Contracts contract = Contracts.builder().id(uid(1)).contractNumber("CNT-WF-006").manager(manager).build();

            agentNotificationService.notifyWorkflowRejected(contract, "no");

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Tacit renewal notifications")
    class TacitRenewal {

        private final Organization org = Organization.builder().id(uid(10)).name("TestOrg").build();

        private Contracts renewing(Managers manager, LocalDate endDate, Integer noticeDays) {
            return Contracts.builder()
                    .id(uid(1)).contractNumber("NRT-119")
                    .counterparty(Counterparty.builder().name("Rossi Impianti").type(CounterpartyType.SUPPLIER).build())
                    .status(ContractStatus.ACTIVE).startDate(LocalDate.of(2026, Month.JANUARY, 1))
                    .endDate(endDate).autoRenew(true).noticeDays(noticeDays).renewalMonths(12)
                    .manager(manager).build();
        }

        private Managers manager() {
            Managers manager = new Managers();
            manager.setId(uid(1));
            manager.setEmail("manager@test.com");
            return manager;
        }

        private void userInOrg() {
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com"))
                    .thenReturn(Optional.of(Users.builder().id(uid(5)).username("mgr").organization(org).build()));
        }

        @Test
        @DisplayName("warns how many days are left to give notice, in the plural")
        void noticeDeadlinePlural() {
            userInOrg();
            LocalDate end = LocalDate.now().plusDays(40);

            agentNotificationService.notifyNoticeDeadline(renewing(manager(), end, 30));

            verify(notificationService).createForUser(uid(5), uid(10), "Termine di disdetta in arrivo",
                    "Restano 10 giorni per disdire il contratto NRT-119 (Rossi Impianti), poi si rinnova automaticamente",
                    NotificationType.WARNING);
        }

        @Test
        @DisplayName("says \"1 giorno\" on the last day but one")
        void noticeDeadlineSingular() {
            userInOrg();

            agentNotificationService.notifyNoticeDeadline(renewing(manager(), LocalDate.now().plusDays(31), 30));

            verify(notificationService).createForUser(any(UUID.class), any(UUID.class), anyString(),
                    contains("Restano 1 giorno per disdire"), any());
        }

        @Test
        @DisplayName("sends nothing without a notice deadline, a manager, an email, a user or an organization")
        void noticeDeadlineNobodyToTell() {
            agentNotificationService.notifyNoticeDeadline(renewing(manager(), LocalDate.now().plusDays(40), null));
            agentNotificationService.notifyNoticeDeadline(renewing(null, LocalDate.now().plusDays(40), 30));
            Managers noEmail = manager();
            noEmail.setEmail(null);
            agentNotificationService.notifyNoticeDeadline(renewing(noEmail, LocalDate.now().plusDays(40), 30));
            when(usersRepository.findByManagerEmailIgnoreCase("manager@test.com"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(Users.builder().id(uid(5)).username("mgr").build()));
            agentNotificationService.notifyNoticeDeadline(renewing(manager(), LocalDate.now().plusDays(40), 30));
            agentNotificationService.notifyNoticeDeadline(renewing(manager(), LocalDate.now().plusDays(40), 30));

            verify(notificationService, never()).createForUser(any(UUID.class), any(UUID.class), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("tells the manager the contract renewed, with the old and new end date")
        void renewed() {
            userInOrg();

            agentNotificationService.notifyTacitRenewal(
                    renewing(manager(), LocalDate.of(2027, Month.DECEMBER, 31), 30), LocalDate.of(2026, Month.DECEMBER, 31));

            verify(notificationService).createForUser(uid(5), uid(10), "Contratto rinnovato tacitamente",
                    "Contratto NRT-119 (Rossi Impianti) si è rinnovato: scadenza spostata dal 31/12/2026 al 31/12/2027",
                    NotificationType.INFO);
        }
    }
}
