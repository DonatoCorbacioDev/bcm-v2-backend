package com.donatodev.bcm_backend.repository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;

@DataJpaTest
@ActiveProfiles("test")
class ContractHistoryRepositoryTest {

    @Autowired
    private ContractHistoryRepository contractHistoryRepository;

    @Autowired
    private ContractsRepository contractsRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private BusinessAreasRepository businessAreasRepository;

    private Contracts contract;
    private Users user;
    private Managers manager;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        contractHistoryRepository.deleteAll();
        contractsRepository.deleteAll();
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();
        businessAreasRepository.deleteAll();

        BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                .name("IT")
                .description("IT area")
                .build());

        manager = managersRepository.save(Managers.builder()
                .firstName("Mario")
                .lastName("Rossi")
                .email("mario.rossi@test.com")
                .phoneNumber("123456")
                .department("Tech")
                .build());

        Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());

        user = usersRepository.save(Users.builder()
                .username("admin")
                .passwordHash("hash")
                .verified(true)
                .role(role)
                .manager(manager)
                .build());

        contract = contractsRepository.save(Contracts.builder()
                .customerName("Customer")
                .contractNumber("CN-001")
                .wbsCode("WBS-001")
                .projectName("Project")
                .businessArea(area)
                .manager(manager)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(6))
                .status(ContractStatus.ACTIVE)
                .build());
    }

    @Nested
    @DisplayName("findByContractId")
    class FindByContractId {

        @Test
        @DisplayName("Should return history entries for the given contract ID")
        void shouldReturnHistoryByContractId() {
            contractHistoryRepository.save(ContractHistory.builder()
                    .contract(contract)
                    .modifiedBy(user)
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.EXPIRED)
                    .build());

            List<ContractHistory> result = contractHistoryRepository.findByContractId(contract.getId());

            assertEquals(1, result.size());
            assertEquals(ContractStatus.ACTIVE, result.get(0).getPreviousStatus());
            assertEquals(ContractStatus.EXPIRED, result.get(0).getNewStatus());
        }

        @Test
        @DisplayName("Should return empty list when no history for the given contract ID")
        void shouldReturnEmptyWhenNoHistoryForContract() {
            List<ContractHistory> result = contractHistoryRepository.findByContractId(9999L);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return multiple history entries for the same contract")
        void shouldReturnMultipleEntriesForSameContract() {
            contractHistoryRepository.save(ContractHistory.builder()
                    .contract(contract)
                    .modifiedBy(user)
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.EXPIRED)
                    .build());

            contractHistoryRepository.save(ContractHistory.builder()
                    .contract(contract)
                    .modifiedBy(user)
                    .previousStatus(ContractStatus.EXPIRED)
                    .newStatus(ContractStatus.CANCELLED)
                    .build());

            List<ContractHistory> result = contractHistoryRepository.findByContractId(contract.getId());

            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("findByContractManagerId")
    class FindByContractManagerId {

        @Test
        @DisplayName("Should return history entries for contracts assigned to the given manager")
        void shouldReturnHistoryByManagerId() {
            contractHistoryRepository.save(ContractHistory.builder()
                    .contract(contract)
                    .modifiedBy(user)
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build());

            List<ContractHistory> result = contractHistoryRepository.findByContractManagerId(manager.getId());

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should return empty list when manager has no associated contract history")
        void shouldReturnEmptyWhenNoHistoryForManager() {
            List<ContractHistory> result = contractHistoryRepository.findByContractManagerId(9999L);

            assertTrue(result.isEmpty());
        }
    }
}
