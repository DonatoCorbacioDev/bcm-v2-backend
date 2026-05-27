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
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;

@DataJpaTest
@ActiveProfiles("test")
class ContractsRepositoryTest {

    @Autowired
    private ContractsRepository contractsRepository;

    @Autowired
    private BusinessAreasRepository businessAreasRepository;

    @Autowired
    private ManagersRepository managersRepository;

    private BusinessAreas area;
    private Managers manager;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        contractsRepository.deleteAll();
        businessAreasRepository.deleteAll();
        managersRepository.deleteAll();

        area = businessAreasRepository.save(BusinessAreas.builder()
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
    }

    private Contracts buildContract(String number, ContractStatus status, Managers mgr, LocalDate start, LocalDate end) {
        return Contracts.builder()
                .customerName("Customer")
                .contractNumber(number)
                .wbsCode("WBS-" + number)
                .projectName("Project " + number)
                .businessArea(area)
                .manager(mgr)
                .startDate(start)
                .endDate(end)
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("findByStatus")
    class FindByStatus {

        @Test
        @DisplayName("Should return contracts matching the given status")
        void shouldReturnContractsByStatus() {
            contractsRepository.save(buildContract("CN-001", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6)));
            contractsRepository.save(buildContract("CN-002", ContractStatus.EXPIRED, manager,
                    LocalDate.now().minusMonths(6), LocalDate.now().minusDays(1)));

            List<Contracts> active = contractsRepository.findByStatus(ContractStatus.ACTIVE);

            assertEquals(1, active.size());
            assertEquals("CN-001", active.get(0).getContractNumber());
        }

        @Test
        @DisplayName("Should return empty list when no contracts match status")
        void shouldReturnEmptyListWhenNoMatch() {
            contractsRepository.save(buildContract("CN-003", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6)));

            List<Contracts> cancelled = contractsRepository.findByStatus(ContractStatus.CANCELLED);

            assertTrue(cancelled.isEmpty());
        }
    }

    @Nested
    @DisplayName("findByManagerId")
    class FindByManagerId {

        @Test
        @DisplayName("Should return contracts assigned to the given manager")
        void shouldReturnContractsByManagerId() {
            Managers anotherManager = managersRepository.save(Managers.builder()
                    .firstName("Luca")
                    .lastName("Bianchi")
                    .email("luca@test.com")
                    .phoneNumber("987654")
                    .department("Sales")
                    .build());

            contractsRepository.save(buildContract("CN-004", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6)));
            contractsRepository.save(buildContract("CN-005", ContractStatus.ACTIVE, anotherManager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6)));

            List<Contracts> result = contractsRepository.findByManagerId(manager.getId());

            assertEquals(1, result.size());
            assertEquals("CN-004", result.get(0).getContractNumber());
        }
    }

    @Nested
    @DisplayName("findByManagerIdAndStatus")
    class FindByManagerIdAndStatus {

        @Test
        @DisplayName("Should return contracts matching both managerId and status")
        void shouldReturnContractsByManagerIdAndStatus() {
            contractsRepository.save(buildContract("CN-006", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6)));
            contractsRepository.save(buildContract("CN-007", ContractStatus.EXPIRED, manager,
                    LocalDate.now().minusMonths(6), LocalDate.now().minusDays(1)));

            List<Contracts> result = contractsRepository.findByManagerIdAndStatus(manager.getId(), ContractStatus.ACTIVE);

            assertEquals(1, result.size());
            assertEquals("CN-006", result.get(0).getContractNumber());
        }
    }

    @Nested
    @DisplayName("count queries")
    class CountQueries {

        @Test
        @DisplayName("Should count all contracts")
        void shouldCountAllContracts() {
            contractsRepository.save(buildContract("CN-010", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6)));
            contractsRepository.save(buildContract("CN-011", ContractStatus.EXPIRED, manager,
                    LocalDate.now().minusMonths(6), LocalDate.now().minusDays(1)));

            int count = contractsRepository.countAllContracts();

            assertEquals(2, count);
        }

        @Test
        @DisplayName("Should count only ACTIVE contracts")
        void shouldCountActiveContracts() {
            contractsRepository.save(buildContract("CN-012", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6)));
            contractsRepository.save(buildContract("CN-013", ContractStatus.EXPIRED, manager,
                    LocalDate.now().minusMonths(6), LocalDate.now().minusDays(1)));

            int count = contractsRepository.countActiveContracts();

            assertEquals(1, count);
        }

        @Test
        @DisplayName("Should count only EXPIRED contracts")
        void shouldCountExpiredContracts() {
            contractsRepository.save(buildContract("CN-014", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6)));
            contractsRepository.save(buildContract("CN-015", ContractStatus.EXPIRED, manager,
                    LocalDate.now().minusMonths(6), LocalDate.now().minusDays(1)));
            contractsRepository.save(buildContract("CN-016", ContractStatus.EXPIRED, manager,
                    LocalDate.now().minusMonths(3), LocalDate.now().minusDays(5)));

            int count = contractsRepository.countExpiredContracts();

            assertEquals(2, count);
        }

        @Test
        @DisplayName("Should count ACTIVE contracts expiring within window")
        void shouldCountExpiringContracts() {
            contractsRepository.save(buildContract("CN-017", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(10)));
            contractsRepository.save(buildContract("CN-018", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(60)));

            int count = contractsRepository.countExpiringContracts(LocalDate.now().plusDays(30));

            assertEquals(1, count);
        }
    }

    @Nested
    @DisplayName("findExpiringContracts")
    class FindExpiringContracts {

        @Test
        @DisplayName("Should return ACTIVE contracts expiring within the given date range")
        void shouldReturnExpiringContracts() {
            LocalDate today = LocalDate.now();
            contractsRepository.save(buildContract("CN-020", ContractStatus.ACTIVE, manager,
                    today.minusMonths(1), today.plusDays(15)));
            contractsRepository.save(buildContract("CN-021", ContractStatus.ACTIVE, manager,
                    today.minusMonths(1), today.plusDays(60)));
            contractsRepository.save(buildContract("CN-022", ContractStatus.EXPIRED, manager,
                    today.minusMonths(6), today.minusDays(1)));

            List<Contracts> expiring = contractsRepository.findExpiringContracts(today, today.plusDays(30));

            assertEquals(1, expiring.size());
            assertEquals("CN-020", expiring.get(0).getContractNumber());
        }

        @Test
        @DisplayName("Should return empty list when no contracts are expiring soon")
        void shouldReturnEmptyWhenNoExpiringContracts() {
            contractsRepository.save(buildContract("CN-023", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(90)));

            List<Contracts> expiring = contractsRepository.findExpiringContracts(
                    LocalDate.now(), LocalDate.now().plusDays(30));

            assertTrue(expiring.isEmpty());
        }
    }

    @Nested
    @DisplayName("findByStatusAndEndDateBetween")
    class FindByStatusAndEndDateBetween {

        @Test
        @DisplayName("Should return contracts matching status and date range")
        void shouldReturnContractsByStatusAndDateRange() {
            LocalDate start = LocalDate.now().minusDays(5);
            LocalDate end = LocalDate.now().plusDays(5);

            contractsRepository.save(buildContract("CN-030", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now()));
            contractsRepository.save(buildContract("CN-031", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(60)));

            List<Contracts> result = contractsRepository.findByStatusAndEndDateBetween(
                    ContractStatus.ACTIVE, start, end);

            assertEquals(1, result.size());
            assertEquals("CN-030", result.get(0).getContractNumber());
        }
    }

    @Nested
    @DisplayName("findByStatusAndEndDateBefore")
    class FindByStatusAndEndDateBefore {

        @Test
        @DisplayName("Should return ACTIVE contracts with end date before the given date")
        void shouldReturnContractsEndingBeforeDate() {
            contractsRepository.save(buildContract("CN-040", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(3), LocalDate.now().minusDays(1)));
            contractsRepository.save(buildContract("CN-041", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(30)));

            List<Contracts> result = contractsRepository.findByStatusAndEndDateBefore(
                    ContractStatus.ACTIVE, LocalDate.now());

            assertEquals(1, result.size());
            assertEquals("CN-040", result.get(0).getContractNumber());
        }

        @Test
        @DisplayName("Should return empty list when no contracts match")
        void shouldReturnEmptyWhenNoMatch() {
            contractsRepository.save(buildContract("CN-042", ContractStatus.ACTIVE, manager,
                    LocalDate.now().minusMonths(1), LocalDate.now().plusDays(30)));

            List<Contracts> result = contractsRepository.findByStatusAndEndDateBefore(
                    ContractStatus.ACTIVE, LocalDate.now());

            assertTrue(result.isEmpty());
        }
    }
}
