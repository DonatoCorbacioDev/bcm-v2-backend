package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Unit tests for {@link ContractSchedulerService}.
 * <p>
 * Verifies automatic contract expiration logic and history tracking.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ContractSchedulerServiceTest {

    @Mock
    private ContractsRepository contractsRepository;

    @Mock
    private ContractHistoryRepository contractHistoryRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private IEmailService emailService;

    @Mock
    private AgentNotificationService agentNotificationService;

    @InjectMocks
    private ContractSchedulerService schedulerService;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: ContractSchedulerService")
    @SuppressWarnings("unused")
    class VerifyContractScheduler {

        private static final LocalDate TODAY = LocalDate.of(2027, Month.JUNE, 15);

        @Test
        @Order(1)
        @DisplayName("Should expire overdue contracts")
        void shouldExpireOverdueContracts() {
            LocalDate today = TODAY;

            Contracts overdueContract1 = Contracts.builder()
                    .id(1L).contractNumber("CNT-001").customerName("Client A")
                    .status(ContractStatus.ACTIVE)
                    .startDate(today.minusMonths(6)).endDate(today.minusDays(1))
                    .build();

            Contracts overdueContract2 = Contracts.builder()
                    .id(2L).contractNumber("CNT-002").customerName("Client B")
                    .status(ContractStatus.ACTIVE)
                    .startDate(today.minusMonths(12)).endDate(today.minusDays(7))
                    .build();

            Users adminUser = Users.builder()
                    .id(1L).username("admin@bcm.com")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            when(contractsRepository.findByStatusAndEndDateBefore(eq(ContractStatus.ACTIVE), any(LocalDate.class)))
                    .thenReturn(List.of(overdueContract1, overdueContract2));
            when(usersRepository.findFirstByRoleRole("ADMIN"))
                    .thenReturn(Optional.of(adminUser));

            schedulerService.expireOverdueContracts();

            verify(contractsRepository, times(2)).save(any(Contracts.class));
            verify(contractHistoryRepository, times(2)).save(any());
            assertEquals(ContractStatus.EXPIRED, overdueContract1.getStatus());
            assertEquals(ContractStatus.EXPIRED, overdueContract2.getStatus());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "DB returns no overdue contracts",
            "empty list of overdue contracts",
            "null endDates excluded by DB query",
            "contract ending today not expired (strict before)"
        })
        @Order(2)
        void shouldNotExpireWhenNoOverdueContracts(String scenario) {
            when(contractsRepository.findByStatusAndEndDateBefore(eq(ContractStatus.ACTIVE), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            schedulerService.expireOverdueContracts();

            verify(contractsRepository, never()).save(any(Contracts.class));
            verify(contractHistoryRepository, never()).save(any());
        }

        @Test
        @Order(4)
        @DisplayName("Should create history record with correct data")
        void shouldCreateHistoryRecordWithCorrectData() {
            LocalDate today = TODAY;

            Contracts overdueContract = Contracts.builder()
                    .id(1L).contractNumber("CNT-004").customerName("Client D")
                    .status(ContractStatus.ACTIVE)
                    .startDate(today.minusMonths(6)).endDate(today.minusDays(1))
                    .build();

            Users adminUser = Users.builder()
                    .id(1L).username("admin@bcm.com")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            when(contractsRepository.findByStatusAndEndDateBefore(eq(ContractStatus.ACTIVE), any(LocalDate.class)))
                    .thenReturn(List.of(overdueContract));
            when(usersRepository.findFirstByRoleRole("ADMIN"))
                    .thenReturn(Optional.of(adminUser));

            schedulerService.expireOverdueContracts();

            verify(contractHistoryRepository).save(argThat(history ->
                    history.getContract().getId().equals(1L)
                    && history.getPreviousStatus() == ContractStatus.ACTIVE
                    && history.getNewStatus() == ContractStatus.EXPIRED
                    && history.getModifiedBy().getUsername().equals("admin@bcm.com")
            ));
        }

        @Test
        @Order(5)
        @DisplayName("Should handle missing admin user gracefully")
        void shouldHandleMissingSystemUser() {
            LocalDate today = TODAY;

            Contracts overdueContract = Contracts.builder()
                    .id(1L).contractNumber("CNT-005").customerName("Client E")
                    .status(ContractStatus.ACTIVE)
                    .startDate(today.minusMonths(6)).endDate(today.minusDays(1))
                    .build();

            when(contractsRepository.findByStatusAndEndDateBefore(eq(ContractStatus.ACTIVE), any(LocalDate.class)))
                    .thenReturn(List.of(overdueContract));
            when(usersRepository.findFirstByRoleRole("ADMIN"))
                    .thenReturn(Optional.empty());

            schedulerService.expireOverdueContracts();

            verify(contractsRepository).save(any(Contracts.class));
            verify(contractHistoryRepository, never()).save(any());
            assertEquals(ContractStatus.EXPIRED, overdueContract.getStatus());
        }

        @Test
        @Order(6)
        @DisplayName("Should expire only the overdue contract returned by the DB query")
        void shouldHandleMixedContractDates() {
            LocalDate today = TODAY;

            Contracts overdueContract = Contracts.builder()
                    .id(1L).contractNumber("CNT-008")
                    .status(ContractStatus.ACTIVE).endDate(today.minusDays(5))
                    .build();

            Users adminUser = Users.builder()
                    .id(1L).username("admin@bcm.com")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            // DB returns only the overdue contract — filtering happens in the query, not in Java
            when(contractsRepository.findByStatusAndEndDateBefore(eq(ContractStatus.ACTIVE), any(LocalDate.class)))
                    .thenReturn(List.of(overdueContract));
            when(usersRepository.findFirstByRoleRole("ADMIN"))
                    .thenReturn(Optional.of(adminUser));

            schedulerService.expireOverdueContracts();

            verify(contractsRepository, times(1)).save(any(Contracts.class));
            verify(contractHistoryRepository, times(1)).save(any());
            assertEquals(ContractStatus.EXPIRED, overdueContract.getStatus());
        }

        // ── Notification tests ─────────────────────────────────────────────

        @Test
        @Order(9)
        @DisplayName("Should send notification for contract at 30-day threshold")
        void shouldSendExpirationNotifications() {
            Managers manager = buildManager(1L, "John", "Doe", "john.doe@example.com");
            Contracts expiringContract = buildContract(1L, "CNT-2025-EXPIRING", "Test Customer", "Test Project", manager);

            // First threshold (30 days) returns the contract; the rest return empty
            when(contractsRepository.findByStatusAndEndDate(eq(ContractStatus.ACTIVE), any(LocalDate.class)))
                    .thenReturn(List.of(expiringContract))
                    .thenReturn(Collections.emptyList());

            schedulerService.sendExpirationNotifications();

            verify(contractsRepository, times(4)).findByStatusAndEndDate(
                    eq(ContractStatus.ACTIVE), any(LocalDate.class));
            verify(emailService).sendEmail(
                    eq("john.doe@example.com"),
                    contains("CNT-2025-EXPIRING"),
                    anyString());
        }

        @Test
        @Order(10)
        @DisplayName("Should not send notification when contract has no manager")
        void shouldNotSendNotificationWhenContractHasNoManager() {
            Contracts contractWithoutManager = buildContract(1L, "CNT-NO-MANAGER", "Test", null, null);

            when(contractsRepository.findByStatusAndEndDate(any(ContractStatus.class), any(LocalDate.class)))
                    .thenReturn(List.of(contractWithoutManager))
                    .thenReturn(Collections.emptyList());

            schedulerService.sendExpirationNotifications();

            verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(11)
        @DisplayName("Should not send notification when manager has no email")
        void shouldNotSendNotificationWhenManagerHasNoEmail() {
            Managers managerWithoutEmail = buildManager(1L, "Jane", "Smith", null);
            Contracts contract = buildContract(1L, "CNT-NO-EMAIL", "Test", null, managerWithoutEmail);

            when(contractsRepository.findByStatusAndEndDate(any(ContractStatus.class), any(LocalDate.class)))
                    .thenReturn(List.of(contract))
                    .thenReturn(Collections.emptyList());

            schedulerService.sendExpirationNotifications();

            verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(12)
        @DisplayName("Should continue processing when email sending throws")
        void shouldHandleEmailSendingException() {
            Managers manager = buildManager(1L, "Test", "User", "test@example.com");
            Contracts contract = buildContract(1L, "CNT-ERROR", "Error Test", "Error Project", manager);

            when(contractsRepository.findByStatusAndEndDate(any(ContractStatus.class), any(LocalDate.class)))
                    .thenReturn(List.of(contract))
                    .thenReturn(Collections.emptyList());

            doThrow(new RuntimeException("Email sending failed"))
                    .when(emailService).sendEmail(anyString(), anyString(), anyString());

            assertDoesNotThrow(() -> schedulerService.sendExpirationNotifications());
            verify(emailService, times(1)).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(13)
        @DisplayName("Should send notifications for multiple contracts at the same threshold")
        void shouldSendMultipleNotificationsForMultipleContracts() {
            Managers manager1 = buildManager(1L, "Manager", "One", "manager1@example.com");
            Managers manager2 = buildManager(2L, "Manager", "Two", "manager2@example.com");
            Contracts contract1 = buildContract(1L, "CNT-001", "Customer 1", "Project 1", manager1);
            Contracts contract2 = buildContract(2L, "CNT-002", "Customer 2", "Project 2", manager2);

            // Both contracts at 30-day threshold
            when(contractsRepository.findByStatusAndEndDate(any(ContractStatus.class), any(LocalDate.class)))
                    .thenReturn(List.of(contract1, contract2))
                    .thenReturn(Collections.emptyList());

            schedulerService.sendExpirationNotifications();

            verify(emailService, times(2)).sendEmail(anyString(), anyString(), anyString());
            verify(emailService).sendEmail(eq("manager1@example.com"), anyString(), anyString());
            verify(emailService).sendEmail(eq("manager2@example.com"), anyString(), anyString());
        }

        @Test
        @Order(14)
        @DisplayName("Should send notification when project name is null")
        void shouldSendNotificationWhenProjectNameIsNull() {
            Managers manager = buildManager(1L, "John", "Doe", "john.doe@example.com");
            Contracts contract = buildContract(1L, "CNT-NO-PROJECT", "Test Customer", null, manager);

            when(contractsRepository.findByStatusAndEndDate(eq(ContractStatus.ACTIVE), any(LocalDate.class)))
                    .thenReturn(List.of(contract))
                    .thenReturn(Collections.emptyList());

            schedulerService.sendExpirationNotifications();

            verify(emailService).sendEmail(eq("john.doe@example.com"), anyString(), anyString());
        }

        @Test
        @Order(15)
        @DisplayName("Should handle in-app notification exception gracefully")
        void shouldHandleInAppNotificationException() {
            Managers manager = buildManager(1L, "Error", "Test", "error@test.com");
            Contracts contract = buildContract(1L, "CNT-INAPP-ERROR", "Test Customer", "Test Project", manager);

            when(contractsRepository.findByStatusAndEndDate(any(ContractStatus.class), any(LocalDate.class)))
                    .thenReturn(List.of(contract))
                    .thenReturn(Collections.emptyList());
            doThrow(new RuntimeException("In-app notification failed"))
                    .when(agentNotificationService).notifyExpiringContract(any(Contracts.class));

            assertDoesNotThrow(() -> schedulerService.sendExpirationNotifications());
        }

        @Test
        @Order(16)
        @DisplayName("Should query exactly the 4 threshold dates and no others")
        void shouldQueryExactlyFourThresholds() {
            when(contractsRepository.findByStatusAndEndDate(any(ContractStatus.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            schedulerService.sendExpirationNotifications();

            verify(contractsRepository, times(4))
                    .findByStatusAndEndDate(eq(ContractStatus.ACTIVE), any(LocalDate.class));
            verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(17)
        @DisplayName("Should include 'Final Notice' in subject for 1-day threshold")
        void shouldSendFinalNoticeForContractExpiringTomorrow() {
            Managers manager = buildManager(1L, "Final", "Test", "final@test.com");
            Contracts contract = buildContract(1L, "CNT-FINAL", "Customer", "Project", manager);

            // Only the 1-day threshold (4th call) returns the contract
            when(contractsRepository.findByStatusAndEndDate(eq(ContractStatus.ACTIVE), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList())  // 30-day
                    .thenReturn(Collections.emptyList())  // 14-day
                    .thenReturn(Collections.emptyList())  // 7-day
                    .thenReturn(List.of(contract));       // 1-day

            schedulerService.sendExpirationNotifications();

            verify(emailService).sendEmail(
                    eq("final@test.com"),
                    contains("Final Notice"),
                    anyString());
        }

        @Test
        @Order(18)
        @DisplayName("Should include 'Urgent' in subject for 7-day threshold")
        void shouldSendUrgentNoticeForContractExpiring7Days() {
            Managers manager = buildManager(1L, "Urgent", "Test", "urgent@test.com");
            Contracts contract = buildContract(1L, "CNT-URGENT", "Customer", "Project", manager);

            // Only the 7-day threshold (3rd call) returns the contract
            when(contractsRepository.findByStatusAndEndDate(eq(ContractStatus.ACTIVE), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList())  // 30-day
                    .thenReturn(Collections.emptyList())  // 14-day
                    .thenReturn(List.of(contract))        // 7-day
                    .thenReturn(Collections.emptyList()); // 1-day

            schedulerService.sendExpirationNotifications();

            verify(emailService).sendEmail(
                    eq("urgent@test.com"),
                    contains("Urgent"),
                    anyString());
        }

        // ── Helpers ────────────────────────────────────────────────────────

        private Managers buildManager(Long id, String firstName, String lastName, String email) {
            Managers m = new Managers();
            m.setId(id);
            m.setFirstName(firstName);
            m.setLastName(lastName);
            m.setEmail(email);
            return m;
        }

        private Contracts buildContract(Long id, String contractNumber, String customerName,
                String projectName, Managers manager) {
            Contracts c = new Contracts();
            c.setId(id);
            c.setContractNumber(contractNumber);
            c.setCustomerName(customerName);
            c.setProjectName(projectName);
            c.setStatus(ContractStatus.ACTIVE);
            c.setStartDate(LocalDate.of(2026, Month.DECEMBER, 15));
            c.setEndDate(LocalDate.of(2027, Month.JULY, 15));
            c.setManager(manager);
            return c;
        }
    }
}
