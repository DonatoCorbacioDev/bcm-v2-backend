package com.donatodev.bcm_backend.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link ContractController}.
 * <p>
 * Tests CRUD operations and filtering on /contracts endpoints, verifying
 * behavior with authenticated users having different roles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContractsRepository contractsRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private BusinessAreasRepository businessAreasRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    /**
     * Clean database before each test to ensure a fresh state.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDb() {
        testDataCleaner.clean();
    }

    /**
     * Nested test class grouping all contract-related API tests.
     */
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("API Verification on Contract")
    @SuppressWarnings("unused")
    class VerificationApiContract {

        /**
         * Test that a new contract can be created successfully.
         */
        @Test
        @Order(1)
        @DisplayName("The new contract has been created")
        @WithMockUser(roles = "ADMIN")
        void shouldCreateContract() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Luca").lastName("Verdi").email("luca@example.com")
                    .phoneNumber("123456").department("Sales").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Operations").description("Ops").build());

            ContractDTO dto = new ContractDTO(null, "Client 1", "CNTR-TEST-1", "WBS-001", "Project X",
                    ContractStatus.ACTIVE, LocalDate.of(2025, 5, 1), LocalDate.of(2026, 5, 1),
                    area.getId(), manager.getId());

            mockMvc.perform(post("/contracts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.contractNumber").value("CNTR-TEST-1"));
        }

        /**
         * Test retrieving all contracts returns the expected list.
         */
        @Test
        @Order(2)
        @DisplayName("All contracts have been retrieved successfully")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldGetAllContracts() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Mario")
                    .lastName("Rossi")
                    .email("mario.rossi@example.com")
                    .phoneNumber("123456789")
                    .department("Admin")
                    .build());

            Roles role = Roles.builder().role("ADMIN").build();
            rolesRepository.save(role);
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("admin")
                    .verified(true)
                    .role(role)
                    .build();
            usersRepository.save(user);

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Dev Area").description("Development").build());

            ContractDTO dto = new ContractDTO(null, "Client A", "CNTR-GET-ALL", "WBS-GET",
                    "Project Get", ContractStatus.ACTIVE,
                    LocalDate.of(2025, 6, 1), LocalDate.of(2026, 6, 1),
                    area.getId(), manager.getId());

            mockMvc.perform(post("/contracts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/contracts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].contractNumber").value("CNTR-GET-ALL"));
        }

        /**
         * Test retrieving a contract by ID returns the correct contract.
         */
        @Test
        @Order(3)
        @DisplayName("The contract has been retrieved successfully by ID")
        @WithMockUser(roles = "ADMIN")
        void shouldGetContractById() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Giulia").lastName("Rossi").email("giulia@example.com")
                    .phoneNumber("222333").department("Marketing").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Marketing").description("Promo").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Client B").contractNumber("CNTR-TEST-3").wbsCode("WBS-003")
                    .projectName("Project B").businessArea(area).manager(manager)
                    .startDate(LocalDate.of(2025, 7, 1)).endDate(LocalDate.of(2026, 7, 1))
                    .status(ContractStatus.ACTIVE).build());

            mockMvc.perform(get("/contracts/{id}", contract.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contractNumber").value("CNTR-TEST-3"));
        }

        /**
         * Test updating a contract modifies and returns updated fields.
         */
        @Test
        @Order(4)
        @DisplayName("The contract has been updated successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldUpdateContract() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Laura").lastName("Bianchi").email("laura@example.com")
                    .phoneNumber("987654").department("Finance").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Finance").description("Money").build());

            Contracts original = contractsRepository.save(Contracts.builder()
                    .customerName("Client Old").contractNumber("CNTR-TEST-4").wbsCode("WBS-OLD")
                    .projectName("Old Project").businessArea(area).manager(manager
            )
                    .startDate(LocalDate.of(2025, 1, 1)).endDate(LocalDate.of(2025, 12, 31))
                    .status(ContractStatus.ACTIVE).build());

            ContractDTO updated = new ContractDTO(original.getId(), "Client Updated", "CNTR-TEST-4", "WBS-NEW",
                    "Updated Project", ContractStatus.EXPIRED,
                    LocalDate.of(2025, 2, 1), LocalDate.of(2025, 11, 30),
                    area.getId(), manager.getId());

            mockMvc.perform(put("/contracts/{id}", original.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updated)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerName").value("Client Updated"))
                    .andExpect(jsonPath("$.status").value("EXPIRED"));
        }

        /**
         * Test deleting a contract returns HTTP 204 No Content.
         */
        @Test
        @Order(5)
        @DisplayName("The contract has been deleted successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldDeleteContract() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Anna").lastName("Marroni").email("anna@example.com")
                    .phoneNumber("000111").department("Legal").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Legal").description("Law").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("To Delete").contractNumber("CNTR-TEST-5").wbsCode("WBS-DEL")
                    .projectName("To be deleted").businessArea(area).manager(manager)
                    .startDate(LocalDate.of(2025, 3, 1)).endDate(LocalDate.of(2025, 9, 30))
                    .status(ContractStatus.CANCELLED).build());

            mockMvc.perform(delete("/contracts/{id}", contract.getId()))
                    .andExpect(status().isNoContent());
        }

        /**
         * Test retrieving a contract with invalid ID returns HTTP 404 Not
         * Found.
         */
        @Test
        @Order(6)
        @DisplayName("Contract not found when searching by invalid ID")
        @WithMockUser(roles = "ADMIN")
        void shouldReturnNotFoundForInvalidId() throws Exception {
            mockMvc.perform(get("/contracts/{id}", 99999L))
                    .andExpect(status().isNotFound());
        }

        /**
         * Test filtering contracts by status returns matching contracts.
         */
        @Test
        @Order(7)
        @DisplayName("The contract has been retrieved successfully by status")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldGetContractsByStatus() throws Exception {
            Roles role = rolesRepository.save(Roles.builder()
                    .role("ADMIN").build());

            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Paolo").lastName("Neri").email("paolo@example.com").department("Ops").build());

            usersRepository.save(Users.builder()
                    .username("admin").passwordHash("admin").role(role).manager(manager).verified(true).build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Ops").description("Operational").build());

            contractsRepository.save(Contracts.builder()
                    .customerName("Client Z").contractNumber("CNTR-STATUS").wbsCode("WBS-STATUS")
                    .projectName("Status Test").businessArea(area).manager(manager)
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
                    .status(ContractStatus.ACTIVE).build());

            mockMvc.perform(get("/contracts/filter")
                    .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].contractNumber").value("CNTR-STATUS"));
        }

        /**
         * Test filtering contracts by invalid status returns HTTP 400 Bad
         * Request.
         */
        @Test
        @Order(8)
        @DisplayName("Should return 400 Bad Request for invalid status")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldReturnBadRequestForInvalidStatus() throws Exception {
            mockMvc.perform(get("/contracts/filter")
                    .param("status", "INVALID_STATUS"))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test retrieving contract statistics.
         */
        @Test
        @Order(9)
        @DisplayName("Should get contract statistics")
        @WithMockUser(roles = "ADMIN")
        void shouldGetContractStats() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Test").lastName("Manager").email("test@example.com")
                    .phoneNumber("123456").department("Test").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Test Area").description("Test").build());

            // Create some test contracts
            contractsRepository.save(Contracts.builder()
                    .customerName("Client 1").contractNumber("STATS-1").wbsCode("WBS-S1")
                    .projectName("Stats Test 1").businessArea(area).manager(manager)
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(60))
                    .status(ContractStatus.ACTIVE).build());

            mockMvc.perform(get("/contracts/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").exists())
                    .andExpect(jsonPath("$.active").exists());
        }

        /**
         * Test searching contracts with pagination.
         */
        @Test
        @Order(10)
        @DisplayName("Should search contracts with pagination")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldSearchContracts() throws Exception {
            Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Search").lastName("Manager").email("search@example.com")
                    .phoneNumber("123456").department("Search").build());

            usersRepository.save(Users.builder()
                    .username("admin").passwordHash("admin").role(role).verified(true).build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Search Area").description("Search").build());

            contractsRepository.save(Contracts.builder()
                    .customerName("SearchClient").contractNumber("SEARCH-1").wbsCode("WBS-SEARCH")
                    .projectName("Search Project").businessArea(area).manager(manager)
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
                    .status(ContractStatus.ACTIVE).build());

            mockMvc.perform(get("/contracts/search")
                    .param("q", "Search")
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        /**
         * Test searching contracts with status filter.
         */
        @Test
        @Order(11)
        @DisplayName("Should search contracts with status filter")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldSearchContractsWithStatus() throws Exception {
            Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Filter").lastName("Manager").email("filter@example.com")
                    .phoneNumber("123456").department("Filter").build());

            usersRepository.save(Users.builder()
                    .username("admin").passwordHash("admin").role(role).verified(true).build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Filter Area").description("Filter").build());

            contractsRepository.save(Contracts.builder()
                    .customerName("FilterClient").contractNumber("FILTER-1").wbsCode("WBS-FILTER")
                    .projectName("Filter Project").businessArea(area).manager(manager)
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
                    .status(ContractStatus.EXPIRED).build());

            mockMvc.perform(get("/contracts/search")
                    .param("status", "EXPIRED")
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        /**
         * Test assigning manager to contract.
         */
        @Test
        @Order(12)
        @DisplayName("Should assign manager to contract")
        @WithMockUser(roles = "ADMIN")
        void shouldAssignManager() throws Exception {
            Managers manager1 = managersRepository.save(Managers.builder()
                    .firstName("Manager1").lastName("One").email("manager1@example.com")
                    .phoneNumber("111111").department("Dept1").build());

            Managers manager2 = managersRepository.save(Managers.builder()
                    .firstName("Manager2").lastName("Two").email("manager2@example.com")
                    .phoneNumber("222222").department("Dept2").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Assign Area").description("Assign").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Assign Client").contractNumber("ASSIGN-1").wbsCode("WBS-ASSIGN")
                    .projectName("Assign Project").businessArea(area).manager(manager1)
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
                    .status(ContractStatus.ACTIVE).build());

            String requestBody = String.format("{\"managerId\": %d}", manager2.getId());

            mockMvc.perform(patch("/contracts/{id}/assign-manager", contract.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isNoContent());
        }

        /**
         * Test getting collaborators for a contract.
         */
        @Test
        @Order(13)
        @DisplayName("Should get collaborators for contract")
        @WithMockUser(roles = "ADMIN")
        void shouldGetCollaborators() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Collab").lastName("Manager").email("collab@example.com")
                    .phoneNumber("123456").department("Collab").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Collab Area").description("Collab").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Collab Client").contractNumber("COLLAB-1").wbsCode("WBS-COLLAB")
                    .projectName("Collab Project").businessArea(area).manager(manager)
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
                    .status(ContractStatus.ACTIVE).build());

            mockMvc.perform(get("/contracts/{id}/collaborators", contract.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        /**
         * Test setting collaborators for a contract.
         */
        @Test
        @Order(14)
        @DisplayName("Should set collaborators for contract")
        @WithMockUser(roles = "ADMIN")
        void shouldSetCollaborators() throws Exception {
            Managers manager1 = managersRepository.save(Managers.builder()
                    .firstName("Set1").lastName("Manager").email("set1@example.com")
                    .phoneNumber("111111").department("Set").build());

            Managers manager2 = managersRepository.save(Managers.builder()
                    .firstName("Set2").lastName("Manager").email("set2@example.com")
                    .phoneNumber("222222").department("Set").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Set Area").description("Set").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Set Client").contractNumber("SET-1").wbsCode("WBS-SET")
                    .projectName("Set Project").businessArea(area).manager(manager1)
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
                    .status(ContractStatus.ACTIVE).build());

            String requestBody = String.format("{\"managerIds\": [%d, %d]}",
                    manager1.getId(), manager2.getId());

            mockMvc.perform(patch("/contracts/{id}/collaborators", contract.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isNoContent());
        }

        /**
         * Test that MANAGER role is forbidden from creating contracts.
         */
        @Test
        @Order(15)
        @DisplayName("MANAGER should be forbidden from creating contracts")
        @WithMockUser(roles = "MANAGER")
        void shouldForbidCreateForManager() throws Exception {
            ContractDTO dto = new ContractDTO(null, "Test", "TEST", "WBS", "Test",
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 1L, 1L);

            mockMvc.perform(post("/contracts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        /**
         * Test that MANAGER can access search endpoint.
         */
        @Test
        @Order(16)
        @DisplayName("MANAGER should be able to search contracts")
        @WithMockUser(username = "manager1", roles = "MANAGER")
        void shouldAllowSearchForManager() throws Exception {
            Roles role = rolesRepository.save(Roles.builder().role("MANAGER").build());
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Manager").lastName("Test").email("mgr@example.com")
                    .phoneNumber("123456").department("Test").build());

            usersRepository.save(Users.builder()
                    .username("manager1").passwordHash("pass").role(role)
                    .manager(manager).verified(true).build());

            mockMvc.perform(get("/contracts/search")
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk());
        }
    }
}
