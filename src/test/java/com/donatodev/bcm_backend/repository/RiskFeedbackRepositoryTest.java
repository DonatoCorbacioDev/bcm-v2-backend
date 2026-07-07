package com.donatodev.bcm_backend.repository;

import java.time.LocalDate;
import java.time.Month;
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
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.RiskFeedback;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;

@DataJpaTest
@ActiveProfiles("test")
class RiskFeedbackRepositoryTest {

    @Autowired
    private RiskFeedbackRepository riskFeedbackRepository;

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

    @Autowired
    private OrganizationRepository organizationRepository;

    private Contracts contract;
    private Users user;
    private Managers manager;
    private Organization organization;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        riskFeedbackRepository.deleteAll();
        contractsRepository.deleteAll();
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();
        businessAreasRepository.deleteAll();
        organizationRepository.deleteAll();

        organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .slug("acme")
                .build());

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
                .contractNumber("RF-REPO-001")
                .businessArea(area)
                .manager(manager)
                .organization(organization)
                .startDate(LocalDate.of(2027, Month.MAY, 15))
                .endDate(LocalDate.of(2027, Month.DECEMBER, 15))
                .status(ContractStatus.ACTIVE)
                .build());
    }

    @Nested
    @DisplayName("findByOrganizationIdOrderByCreatedAtDescIdDesc")
    class FindByOrganizationId {

        @Test
        @DisplayName("Should return feedback entries for the given organization")
        void shouldReturnByOrganization() {
            riskFeedbackRepository.save(RiskFeedback.builder()
                    .contract(contract)
                    .submittedBy(user)
                    .organizationId(organization.getId())
                    .riskScore(0.6)
                    .riskLevel("MEDIUM")
                    .agree(true)
                    .build());

            List<RiskFeedback> result = riskFeedbackRepository.findByOrganizationIdOrderByCreatedAtDescIdDesc(organization.getId());

            assertEquals(1, result.size());
            assertEquals("MEDIUM", result.get(0).getRiskLevel());
        }

        @Test
        @DisplayName("Should return empty list for an organization with no feedback")
        void shouldReturnEmptyWhenNoFeedback() {
            List<RiskFeedback> result = riskFeedbackRepository.findByOrganizationIdOrderByCreatedAtDescIdDesc(9999L);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("findByContractManagerIdOrderByCreatedAtDescIdDesc")
    class FindByContractManagerId {

        @Test
        @DisplayName("Should return feedback entries for contracts assigned to the given manager")
        void shouldReturnByManagerId() {
            riskFeedbackRepository.save(RiskFeedback.builder()
                    .contract(contract)
                    .submittedBy(user)
                    .organizationId(organization.getId())
                    .riskScore(0.9)
                    .riskLevel("HIGH")
                    .agree(false)
                    .build());

            List<RiskFeedback> result = riskFeedbackRepository.findByContractManagerIdOrderByCreatedAtDescIdDesc(manager.getId());

            assertEquals(1, result.size());
            assertEquals(false, result.get(0).isAgree());
        }

        @Test
        @DisplayName("Should return empty list when manager has no associated feedback")
        void shouldReturnEmptyWhenNoFeedbackForManager() {
            List<RiskFeedback> result = riskFeedbackRepository.findByContractManagerIdOrderByCreatedAtDescIdDesc(9999L);

            assertTrue(result.isEmpty());
        }
    }
}
