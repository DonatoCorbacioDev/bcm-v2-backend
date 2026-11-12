package com.donatodev.bcm_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.support.TestTenant;
import com.donatodev.bcm_backend.util.TestDataCleaner;

/**
 * Integration tests for {@link ContractHistoryController}: the history is read
 * through the API and written only by the server, on every status change
 * (OWASP API3). There is no endpoint to add or delete an entry, so an entry
 * can't be signed with a colleague's name, backdated, or erased.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractHistoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ContractsRepository contractsRepository;
    @Autowired private ContractHistoryRepository historyRepository;
    @Autowired private CounterpartiesRepository counterpartiesRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private ManagersRepository managersRepository;
    @Autowired private BusinessAreasRepository businessAreasRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private TestDataCleaner testDataCleaner;

    private Organization testOrg;
    private Contracts contract;
    private Users user;

    @AfterEach
    void leaveTestTenant() {
        TestTenant.clear();
    }

    @BeforeEach
    void setUp() {
        testDataCleaner.clean();
        // TestDataCleaner predates the counterparties table.
        counterpartiesRepository.deleteAll();
        testOrg = organizationRepository.save(Organization.builder()
                .name("Test org").slug("test-org-" + UUID.randomUUID()).build());
        TestTenant.use(testOrg.getId());

        Managers manager = managersRepository.save(Managers.builder().organization(testOrg)
                .firstName("Anna").lastName("Verdi").email("anna.verdi@example.com").department("Operations").build());
        BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder().organization(testOrg)
                .name("Operations Area").description("Operations details").build());
        Counterparty counterparty = counterpartiesRepository.save(Counterparty.builder().organization(testOrg)
                .name("Big Client").type(CounterpartyType.CUSTOMER).build());
        contract = contractsRepository.save(Contracts.builder().organization(testOrg)
                .counterparty(counterparty).contractNumber("CH001").wbsCode("WBS001").projectName("Operations Project")
                .businessArea(area).manager(manager)
                .startDate(LocalDate.of(2027, Month.JUNE, 15)).endDate(LocalDate.of(2027, Month.DECEMBER, 15))
                .status(ContractStatus.ACTIVE).build());
        Roles role = rolesRepository.findByRole("ADMIN")
                .orElseGet(() -> rolesRepository.save(Roles.builder().role("ADMIN").build()));
        user = usersRepository.save(Users.builder().organization(testOrg)
                .username("anna.verdi").passwordHash("hash").manager(manager).role(role).build());
    }

    /** What the server writes when a contract's status changes. */
    private ContractHistory entry(ContractStatus from, ContractStatus to, LocalDateTime at) {
        return historyRepository.save(ContractHistory.builder().contract(contract).modifiedBy(user)
                .modificationDate(at).previousStatus(from).newStatus(to).build());
    }

    @Nested
    @DisplayName("Reading the history")
    class Reading {

        @Test
        @DisplayName("one entry by id")
        @WithMockUser(username = "anna.verdi", roles = "ADMIN")
        void shouldGetContractHistoryById() throws Exception {
            ContractHistory saved = entry(ContractStatus.ACTIVE, ContractStatus.EXPIRED, LocalDateTime.of(2027, 6, 15, 12, 0));

            mockMvc.perform(get("/contract-history/{id}", saved.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contractId").value(contract.getId().toString()))
                    .andExpect(jsonPath("$.modifiedById").value(user.getId().toString()))
                    .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.newStatus").value("EXPIRED"));
        }

        @Test
        @DisplayName("every entry of one contract")
        @WithMockUser(username = "anna.verdi", roles = "ADMIN")
        void shouldGetAllContractHistoriesByContractId() throws Exception {
            entry(ContractStatus.ACTIVE, ContractStatus.EXPIRED, LocalDateTime.of(2020, 1, 1, 12, 0));
            entry(ContractStatus.EXPIRED, ContractStatus.CANCELLED, LocalDateTime.of(2027, 6, 15, 12, 0));

            mockMvc.perform(get("/contract-history/contract/{contractId}", contract.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("every entry of the organization, for an admin")
        @WithMockUser(username = "anna.verdi", roles = "ADMIN")
        void shouldGetAllContractHistories() throws Exception {
            entry(ContractStatus.ACTIVE, ContractStatus.CANCELLED, LocalDateTime.of(2027, 6, 15, 12, 0));

            mockMvc.perform(get("/contract-history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Nested
    @DisplayName("Writing the history is the server's alone")
    class NoWrites {

        @Test
        @DisplayName("an admin can't add an entry signed by a colleague, nor backdate one")
        @WithMockUser(username = "anna.verdi", roles = "ADMIN")
        void noManualEntries() throws Exception {
            mockMvc.perform(post("/contract-history")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contractId\":\"" + contract.getId() + "\",\"modifiedById\":\"" + user.getId()
                                    + "\",\"modificationDate\":\"2020-01-01T00:00:00\",\"previousStatus\":\"DRAFT\",\"newStatus\":\"ACTIVE\"}"))
                    .andExpect(status().isMethodNotAllowed());
            org.assertj.core.api.Assertions.assertThat(historyRepository.count()).isZero();
        }

        @Test
        @DisplayName("an admin can't erase an entry")
        @WithMockUser(username = "anna.verdi", roles = "ADMIN")
        void noDeletion() throws Exception {
            ContractHistory saved = entry(ContractStatus.ACTIVE, ContractStatus.CANCELLED, LocalDateTime.of(2027, 6, 15, 12, 0));

            mockMvc.perform(delete("/contract-history/{id}", saved.getId()))
                    .andExpect(status().isMethodNotAllowed());
            org.assertj.core.api.Assertions.assertThat(historyRepository.existsById(saved.getId())).isTrue();
        }
    }
}
