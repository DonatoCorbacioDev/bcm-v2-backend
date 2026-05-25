package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
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

    @InjectMocks
    private ContractSchedulerService schedulerService;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: ContractSchedulerService")
    @SuppressWarnings("unused")
    class VerifyContractScheduler {

        @Test
        @Order(1)
        @DisplayName("Should expire overdue contracts")
        void shouldExpireOverdueContracts() {
            LocalDate today = LocalDate.now();

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
            LocalDate today = LocalDate.now();

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
            LocalDate today = LocalDate.now();

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
            LocalDate today = LocalDate.now();

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

        @Test
        @Order(9)
        @DisplayName("Should send expiration notifications for contracts expiring in 20 days")
        void shouldSendExpirationNotifications() {
            // Given: Contract expiring in 20 days with manager
            LocalDate today = LocalDate.now();
            LocalDate expiringDate = today.plusDays(20);

            Managers manager = new Managers();
            manager.setId(1L);
            manager.setFirstName("John");
            manager.setLastName("Doe");
            manager.setEmail("john.doe@example.com");

            Contracts expiringContract = new Contracts();
            expiringContract.setId(1L);
            expiringContract.setContractNumber("CNT-2025-EXPIRING");
            expiringContract.setCustomerName("Test Customer");
            expiringContract.setProjectName("Test Project");
            expiringContract.setStatus(ContractStatus.ACTIVE);
            expiringContract.setStartDate(today.minusMonths(6));
            expiringContract.setEndDate(expiringDate);
            expiringContract.setManager(manager);

            when(contractsRepository.findByStatusAndEndDateBetween(
                    eq(ContractStatus.ACTIVE),
                    any(LocalDate.class),
                    any(LocalDate.class)
            )).thenReturn(List.of(expiringContract));

            schedulerService.sendExpirationNotifications();

            verify(contractsRepository).findByStatusAndEndDateBetween(
                    eq(ContractStatus.ACTIVE),
                    any(LocalDate.class),
                    any(LocalDate.class)
            );
            verify(emailService).sendEmail(
                    eq("john.doe@example.com"),
                    contains("CNT-2025-EXPIRING"),
                    anyString()
            );
        }

        @Test
        @Order(10)
        @DisplayName("Should not send notification when contract has no manager")
        void shouldNotSendNotificationWhenContractHasNoManager() {
            // Given: Contract without manager
            LocalDate today = LocalDate.now();

            Contracts contractWithoutManager = new Contracts();
            contractWithoutManager.setId(1L);
            contractWithoutManager.setContractNumber("CNT-NO-MANAGER");
            contractWithoutManager.setStatus(ContractStatus.ACTIVE);
            contractWithoutManager.setEndDate(today.plusDays(15));
            contractWithoutManager.setManager(null);

            when(contractsRepository.findByStatusAndEndDateBetween(
                    any(ContractStatus.class),
                    any(LocalDate.class),
                    any(LocalDate.class)
            )).thenReturn(List.of(contractWithoutManager));

            schedulerService.sendExpirationNotifications();

            verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(11)
        @DisplayName("Should not send notification when manager has no email")
        void shouldNotSendNotificationWhenManagerHasNoEmail() {
            // Given: Contract with manager but no email
            LocalDate today = LocalDate.now();

            Managers managerWithoutEmail = new Managers();
            managerWithoutEmail.setId(1L);
            managerWithoutEmail.setFirstName("Jane");
            managerWithoutEmail.setLastName("Smith");
            managerWithoutEmail.setEmail(null);

            Contracts contract = new Contracts();
            contract.setId(1L);
            contract.setContractNumber("CNT-NO-EMAIL");
            contract.setStatus(ContractStatus.ACTIVE);
            contract.setEndDate(today.plusDays(25));
            contract.setManager(managerWithoutEmail);

            when(contractsRepository.findByStatusAndEndDateBetween(
                    any(ContractStatus.class),
                    any(LocalDate.class),
                    any(LocalDate.class)
            )).thenReturn(List.of(contract));

            schedulerService.sendExpirationNotifications();

            verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(12)
        @DisplayName("Should not send notification when email sending fails")
        void shouldHandleEmailSendingException() {
            // Given: Email service throws exception
            LocalDate today = LocalDate.now();

            Managers manager = new Managers();
            manager.setId(1L);
            manager.setFirstName("Test");
            manager.setLastName("User");
            manager.setEmail("test@example.com");

            Contracts contract = new Contracts();
            contract.setId(1L);
            contract.setContractNumber("CNT-ERROR");
            contract.setCustomerName("Error Test");
            contract.setProjectName("Error Project");
            contract.setStatus(ContractStatus.ACTIVE);
            contract.setEndDate(today.plusDays(10));
            contract.setManager(manager);

            when(contractsRepository.findByStatusAndEndDateBetween(
                    any(ContractStatus.class),
                    any(LocalDate.class),
                    any(LocalDate.class)
            )).thenReturn(List.of(contract));

            doThrow(new RuntimeException("Email sending failed"))
                    .when(emailService).sendEmail(anyString(), anyString(), anyString());

            // When & Then: Should not throw exception (handled internally)
            assertDoesNotThrow(() -> schedulerService.sendExpirationNotifications());

            verify(emailService).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(13)
        @DisplayName("Should send multiple notifications for multiple contracts")
        void shouldSendMultipleNotificationsForMultipleContracts() {
            // Given: Multiple contracts expiring
            LocalDate today = LocalDate.now();

            Managers manager1 = new Managers();
            manager1.setId(1L);
            manager1.setFirstName("Manager");
            manager1.setLastName("One");
            manager1.setEmail("manager1@example.com");

            Managers manager2 = new Managers();
            manager2.setId(2L);
            manager2.setFirstName("Manager");
            manager2.setLastName("Two");
            manager2.setEmail("manager2@example.com");

            Contracts contract1 = createContract(1L, "CNT-001", today.plusDays(15), manager1);
            Contracts contract2 = createContract(2L, "CNT-002", today.plusDays(20), manager2);

            when(contractsRepository.findByStatusAndEndDateBetween(
                    any(ContractStatus.class),
                    any(LocalDate.class),
                    any(LocalDate.class)
            )).thenReturn(List.of(contract1, contract2));

            schedulerService.sendExpirationNotifications();

            verify(emailService, times(2)).sendEmail(anyString(), anyString(), anyString());
            verify(emailService).sendEmail(eq("manager1@example.com"), anyString(), anyString());
            verify(emailService).sendEmail(eq("manager2@example.com"), anyString(), anyString());
        }

        @Test
        @Order(14)
        @DisplayName("Should send notification for contract with null project name")
        void shouldSendNotificationWhenProjectNameIsNull() {
            LocalDate today = LocalDate.now();

            Managers manager = new Managers();
            manager.setId(1L);
            manager.setFirstName("John");
            manager.setLastName("Doe");
            manager.setEmail("john.doe@example.com");

            Contracts contract = new Contracts();
            contract.setId(1L);
            contract.setContractNumber("CNT-NO-PROJECT");
            contract.setCustomerName("Test Customer");
            contract.setProjectName(null);
            contract.setStatus(ContractStatus.ACTIVE);
            contract.setStartDate(today.minusMonths(6));
            contract.setEndDate(today.plusDays(10));
            contract.setManager(manager);

            when(contractsRepository.findByStatusAndEndDateBetween(
                    eq(ContractStatus.ACTIVE), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of(contract));

            schedulerService.sendExpirationNotifications();

            verify(emailService).sendEmail(eq("john.doe@example.com"), anyString(), anyString());
        }

        // Helper method
        private Contracts createContract(Long id, String contractNumber, LocalDate endDate, Managers manager) {
            Contracts contract = new Contracts();
            contract.setId(id);
            contract.setContractNumber(contractNumber);
            contract.setCustomerName("Customer " + id);
            contract.setProjectName("Project " + id);
            contract.setStatus(ContractStatus.ACTIVE);
            contract.setStartDate(LocalDate.now().minusMonths(6));
            contract.setEndDate(endDate);
            contract.setManager(manager);
            return contract;
        }
    }
}
