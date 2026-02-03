package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
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
            LocalDate yesterday = today.minusDays(1);
            LocalDate lastWeek = today.minusDays(7);

            Contracts overdueContract1 = Contracts.builder()
                    .id(1L)
                    .contractNumber("CNT-001")
                    .customerName("Client A")
                    .status(ContractStatus.ACTIVE)
                    .startDate(today.minusMonths(6))
                    .endDate(yesterday)
                    .build();

            Contracts overdueContract2 = Contracts.builder()
                    .id(2L)
                    .contractNumber("CNT-002")
                    .customerName("Client B")
                    .status(ContractStatus.ACTIVE)
                    .startDate(today.minusMonths(12))
                    .endDate(lastWeek)
                    .build();

            Users systemUser = Users.builder()
                    .id(1L)
                    .username("admin@example.com")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            when(contractsRepository.findByStatus(ContractStatus.ACTIVE))
                    .thenReturn(List.of(overdueContract1, overdueContract2));
            when(usersRepository.findByUsername("admin@example.com"))
                    .thenReturn(Optional.of(systemUser));

            schedulerService.expireOverdueContracts();

            verify(contractsRepository, times(2)).save(any(Contracts.class));
            verify(contractHistoryRepository, times(2)).save(any());

            assertEquals(ContractStatus.EXPIRED, overdueContract1.getStatus());
            assertEquals(ContractStatus.EXPIRED, overdueContract2.getStatus());
        }

        @Test
        @Order(2)
        @DisplayName("Should not expire contracts with future end dates")
        void shouldNotExpireFutureContracts() {

            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            LocalDate nextWeek = today.plusDays(7);

            Contracts activeContract = Contracts.builder()
                    .id(1L)
                    .contractNumber("CNT-003")
                    .customerName("Client C")
                    .status(ContractStatus.ACTIVE)
                    .startDate(today.minusMonths(3))
                    .endDate(nextWeek)
                    .build();

            when(contractsRepository.findByStatus(ContractStatus.ACTIVE))
                    .thenReturn(List.of(activeContract));

            schedulerService.expireOverdueContracts();

            verify(contractsRepository, never()).save(any(Contracts.class));
            verify(contractHistoryRepository, never()).save(any());

            assertEquals(ContractStatus.ACTIVE, activeContract.getStatus());
        }

        @Test
        @Order(3)
        @DisplayName("Should handle empty list of active contracts")
        void shouldHandleEmptyContractList() {

            when(contractsRepository.findByStatus(ContractStatus.ACTIVE))
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
            LocalDate yesterday = today.minusDays(1);

            Contracts overdueContract = Contracts.builder()
                    .id(1L)
                    .contractNumber("CNT-004")
                    .customerName("Client D")
                    .status(ContractStatus.ACTIVE)
                    .startDate(today.minusMonths(6))
                    .endDate(yesterday)
                    .build();

            Users systemUser = Users.builder()
                    .id(1L)
                    .username("admin@example.com")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            when(contractsRepository.findByStatus(ContractStatus.ACTIVE))
                    .thenReturn(List.of(overdueContract));
            when(usersRepository.findByUsername("admin@example.com"))
                    .thenReturn(Optional.of(systemUser));

            schedulerService.expireOverdueContracts();

            verify(contractHistoryRepository).save(argThat(history
                    -> history.getContract().getId().equals(1L)
                    && history.getPreviousStatus() == ContractStatus.ACTIVE
                    && history.getNewStatus() == ContractStatus.EXPIRED
                    && history.getModifiedBy().getUsername().equals("admin@example.com")
            ));
        }

        @Test
        @Order(5)
        @DisplayName("Should handle missing system user gracefully")
        void shouldHandleMissingSystemUser() {

            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);

            Contracts overdueContract = Contracts.builder()
                    .id(1L)
                    .contractNumber("CNT-005")
                    .customerName("Client E")
                    .status(ContractStatus.ACTIVE)
                    .startDate(today.minusMonths(6))
                    .endDate(yesterday)
                    .build();

            when(contractsRepository.findByStatus(ContractStatus.ACTIVE))
                    .thenReturn(List.of(overdueContract));
            when(usersRepository.findByUsername("admin@example.com"))
                    .thenReturn(Optional.empty());

            schedulerService.expireOverdueContracts();

            verify(contractsRepository).save(any(Contracts.class));
            verify(contractHistoryRepository, never()).save(any());

            assertEquals(ContractStatus.EXPIRED, overdueContract.getStatus());
        }

        @Test
        @Order(6)
        @DisplayName("Should handle contracts with null end date")
        void shouldHandleContractsWithNullEndDate() {

            Contracts contractWithoutEndDate = Contracts.builder()
                    .id(1L)
                    .contractNumber("CNT-006")
                    .customerName("Client F")
                    .status(ContractStatus.ACTIVE)
                    .startDate(LocalDate.now().minusMonths(6))
                    .endDate(null)
                    .build();

            when(contractsRepository.findByStatus(ContractStatus.ACTIVE))
                    .thenReturn(List.of(contractWithoutEndDate));

            schedulerService.expireOverdueContracts();

            verify(contractsRepository, never()).save(any(Contracts.class));
            verify(contractHistoryRepository, never()).save(any());

            assertEquals(ContractStatus.ACTIVE, contractWithoutEndDate.getStatus());
        }

        @Test
        @Order(7)
        @DisplayName("Should expire contract with end date equal to today")
        void shouldNotExpireContractEndingToday() {

            LocalDate today = LocalDate.now();

            Contracts contractEndingToday = Contracts.builder()
                    .id(1L)
                    .contractNumber("CNT-007")
                    .customerName("Client G")
                    .status(ContractStatus.ACTIVE)
                    .startDate(today.minusMonths(6))
                    .endDate(today)
                    .build();

            when(contractsRepository.findByStatus(ContractStatus.ACTIVE))
                    .thenReturn(List.of(contractEndingToday));

            schedulerService.expireOverdueContracts();

            // Contract ending TODAY should NOT be expired (only those BEFORE today)
            verify(contractsRepository, never()).save(any(Contracts.class));
            verify(contractHistoryRepository, never()).save(any());

            assertEquals(ContractStatus.ACTIVE, contractEndingToday.getStatus());
        }

        @Test
        @Order(8)
        @DisplayName("Should handle multiple contracts with mixed dates")
        void shouldHandleMixedContractDates() {

            LocalDate today = LocalDate.now();

            Contracts overdueContract = Contracts.builder()
                    .id(1L)
                    .contractNumber("CNT-008")
                    .status(ContractStatus.ACTIVE)
                    .endDate(today.minusDays(5))
                    .build();

            Contracts activeContract = Contracts.builder()
                    .id(2L)
                    .contractNumber("CNT-009")
                    .status(ContractStatus.ACTIVE)
                    .endDate(today.plusDays(10))
                    .build();

            Contracts contractWithoutEndDate = Contracts.builder()
                    .id(3L)
                    .contractNumber("CNT-010")
                    .status(ContractStatus.ACTIVE)
                    .endDate(null)
                    .build();

            Users systemUser = Users.builder()
                    .id(1L)
                    .username("admin@example.com")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            when(contractsRepository.findByStatus(ContractStatus.ACTIVE))
                    .thenReturn(List.of(overdueContract, activeContract, contractWithoutEndDate));
            when(usersRepository.findByUsername("admin@example.com"))
                    .thenReturn(Optional.of(systemUser));

            schedulerService.expireOverdueContracts();

            verify(contractsRepository, times(1)).save(any(Contracts.class));
            verify(contractHistoryRepository, times(1)).save(any());

            assertEquals(ContractStatus.EXPIRED, overdueContract.getStatus());
            assertEquals(ContractStatus.ACTIVE, activeContract.getStatus());
            assertEquals(ContractStatus.ACTIVE, contractWithoutEndDate.getStatus());
        }
    }
}
