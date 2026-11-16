package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;

@DataJpaTest
@ActiveProfiles("test")
class ContractsRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2027, Month.JUNE, 15);

    @Autowired
    private ContractsRepository contractsRepository;

    @Autowired
    private BusinessAreasRepository businessAreasRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private CounterpartiesRepository counterpartiesRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private BusinessAreas area;
    private Managers manager;
    private Counterparty counterparty;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        contractsRepository.deleteAll();
        businessAreasRepository.deleteAll();
        managersRepository.deleteAll();
        counterpartiesRepository.deleteAll();

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

        counterparty = counterpartiesRepository.save(Counterparty.builder()
                .name("Customer")
                .type(CounterpartyType.CUSTOMER)
                .build());
    }

    private Contracts buildContract(String number, ContractStatus status, Managers mgr, LocalDate start, LocalDate end) {
        return Contracts.builder()
                .counterparty(counterparty)
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

    @Test
    void shouldAllowSameNumberInDifferentOrganizations() {
        Organization orgA = organizationRepository.save(Organization.builder().name("A").slug("number-test-a").build());
        Organization orgB = organizationRepository.save(Organization.builder().name("B").slug("number-test-b").build());
        contractsRepository.saveAndFlush(tenantContract("SAME-NUMBER", orgA));

        assertTrue(contractsRepository.existsByContractNumberAndOrganizationId("SAME-NUMBER", orgA.getId()));
        assertFalse(
                contractsRepository.existsByContractNumberAndOrganizationId("SAME-NUMBER", orgB.getId()));

        contractsRepository.saveAndFlush(tenantContract("SAME-NUMBER", orgB));
        assertTrue(contractsRepository.existsByContractNumberAndOrganizationId("SAME-NUMBER", orgB.getId()));
    }

    @Test
    void shouldRejectSameNumberWithinOrganization() {
        Organization org = organizationRepository.save(Organization.builder().name("A").slug("number-test-a").build());
        contractsRepository.saveAndFlush(tenantContract("SAME-NUMBER", org));
        Contracts duplicate = tenantContract("SAME-NUMBER", org);

        assertThrows(DataIntegrityViolationException.class,
                () -> contractsRepository.saveAndFlush(duplicate));
    }

    private Contracts tenantContract(String number, Organization org) {
        BusinessAreas tenantArea = businessAreasRepository.save(BusinessAreas.builder().organization(org)
                .name(UUID.randomUUID().toString()).build());
        Counterparty tenantCounterparty = counterpartiesRepository.save(Counterparty.builder().organization(org)
                .name("Tenant customer").type(CounterpartyType.CUSTOMER).build());
        return Contracts.builder().organization(org).businessArea(tenantArea).counterparty(tenantCounterparty)
                .contractNumber(number).startDate(TODAY).status(ContractStatus.ACTIVE).build();
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
                    TODAY.minusMonths(1), TODAY.plusMonths(6)));
            contractsRepository.save(buildContract("CN-005", ContractStatus.ACTIVE, anotherManager,
                    TODAY.minusMonths(1), TODAY.plusMonths(6)));

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
                    TODAY.minusMonths(1), TODAY.plusMonths(6)));
            contractsRepository.save(buildContract("CN-007", ContractStatus.EXPIRED, manager,
                    TODAY.minusMonths(6), TODAY.minusDays(1)));

            List<Contracts> result = contractsRepository.findByManagerIdAndStatus(manager.getId(), ContractStatus.ACTIVE);

            assertEquals(1, result.size());
            assertEquals("CN-006", result.get(0).getContractNumber());
        }
    }

    @Nested
    @DisplayName("findByStatusAndEndDateBefore")
    class FindByStatusAndEndDateBefore {

        @Test
        @DisplayName("Should return ACTIVE contracts with end date before the given date")
        void shouldReturnContractsEndingBeforeDate() {
            contractsRepository.save(buildContract("CN-040", ContractStatus.ACTIVE, manager,
                    TODAY.minusMonths(3), TODAY.minusDays(1)));
            contractsRepository.save(buildContract("CN-041", ContractStatus.ACTIVE, manager,
                    TODAY.minusMonths(1), TODAY.plusDays(30)));

            List<Contracts> result = contractsRepository.findByStatusAndEndDateBefore(
                    ContractStatus.ACTIVE, TODAY);

            assertEquals(1, result.size());
            assertEquals("CN-040", result.get(0).getContractNumber());
        }

        @Test
        @DisplayName("Should return empty list when no contracts match")
        void shouldReturnEmptyWhenNoMatch() {
            contractsRepository.save(buildContract("CN-042", ContractStatus.ACTIVE, manager,
                    TODAY.minusMonths(1), TODAY.plusDays(30)));

            List<Contracts> result = contractsRepository.findByStatusAndEndDateBefore(
                    ContractStatus.ACTIVE, TODAY);

            assertTrue(result.isEmpty());
        }
    }
}
