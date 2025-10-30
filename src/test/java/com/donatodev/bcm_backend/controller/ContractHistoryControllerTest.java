package com.donatodev.bcm_backend.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.ContractHistoryDTO;
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
 * Integration tests for {@link com.donatodev.bcm_backend.controller.ContractHistoryController}.
 * <p>
 * Tests REST APIs for contract history management, verifying
 * CRUD operations and filtered retrieval functionality.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractHistoryControllerTest {
	
	@Autowired 
    private MockMvc mockMvc;
    
    @Autowired 
    private ObjectMapper objectMapper;
    
    @Autowired 
    private ContractsRepository contractsRepository;
    
    @Autowired 
    private UsersRepository usersRepository;
    
    @Autowired 
    private ManagersRepository managersRepository;
    
    @Autowired 
    private BusinessAreasRepository businessAreasRepository;
    
    @Autowired 
    private RolesRepository rolesRepository;
    
    @Autowired
    private TestDataCleaner testDataCleaner;

    /**
     * Cleans the database before each test to ensure a clean state.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDb() {
        testDataCleaner.clean();
    }

    /**
     * Nested class grouping contract history API tests.
     */
    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("API Verification on Contract History")
    @SuppressWarnings("unused")
    class VerificationApiContractHistory {

        /**
         * Tests creating a new contract history record.
         */
        @Test
        @Order(1)
        @DisplayName("The new contract history has been created")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldCreateContractHistory() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Marco").lastName("Rossi").email("marco.rossi@example.com").department("Legal").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Legal Area").description("Handles legal affairs").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Cliente Uno").contractNumber("CH001").wbsCode("WBS001").projectName("Legal Project")
                    .businessArea(area).manager(manager)
                    .startDate(LocalDateTime.now().toLocalDate()).endDate(LocalDateTime.now().plusDays(365).toLocalDate())
                    .status(ContractStatus.ACTIVE).build());

            Roles savedRole = rolesRepository.save(Roles.builder().role("ADMIN").build());

            Users user = usersRepository.save(Users.builder()
                    .username("admin").passwordHash("hashedpassword").manager(manager).role(savedRole).build());

            ContractHistoryDTO dto = new ContractHistoryDTO(null, contract.getId(), user.getId(),
                    LocalDateTime.now(), ContractStatus.ACTIVE, ContractStatus.CANCELLED);

            mockMvc.perform(post("/contract-history")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.newStatus").value("CANCELLED"));
        }

        /**
         * Tests retrieving a contract history record by its ID.
         */
        @Test
        @Order(2)
        @DisplayName("The contract history has been retrieved successfully")
        @WithMockUser(username = "elena.hr", roles = "ADMIN")
        void shouldGetContractHistoryById() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Elena").lastName("Bianchi").email("elena.bianchi@example.com").department("HR").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("HR Area").description("Handles human resources").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Client Beta").contractNumber("CH002").wbsCode("WBS002").projectName("HR Project")
                    .businessArea(area).manager(manager)
                    .startDate(LocalDateTime.now().toLocalDate()).endDate(LocalDateTime.now().plusMonths(6).toLocalDate())
                    .status(ContractStatus.ACTIVE).build());

            Roles savedRole = rolesRepository.findByRole("ADMIN")
                    .orElseGet(() -> rolesRepository.save(Roles.builder().role("ADMIN").build()));

            Users user = usersRepository.save(Users.builder()
                    .username("elena.hr").passwordHash("passwordhr").manager(manager).role(savedRole).build());

            ContractHistoryDTO dto = new ContractHistoryDTO(null, contract.getId(), user.getId(),
                    LocalDateTime.now(), ContractStatus.ACTIVE, ContractStatus.EXPIRED);

            String response = mockMvc.perform(post("/contract-history")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long id = objectMapper.readTree(response).get("id").asLong();

            mockMvc.perform(get("/contract-history/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contractId").value(contract.getId()))
                    .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.newStatus").value("EXPIRED"));
        }

        /**
         * Tests retrieving all contract history entries for a specific contract.
         */
        @Test
        @Order(3)
        @DisplayName("All contract histories have been retrieved successfully for a specific contract")
        @WithMockUser(username = "anna.verdi", roles = "ADMIN")
        void shouldGetAllContractHistoriesByContractId() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Anna").lastName("Verdi").email("anna.verdi@example.com").department("Operations").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Operations Area").description("Operations details").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Big Client").contractNumber("CH003").wbsCode("WBS003").projectName("Operations Project")
                    .businessArea(area).manager(manager)
                    .startDate(LocalDateTime.now().toLocalDate()).endDate(LocalDateTime.now().plusMonths(6).toLocalDate())
                    .status(ContractStatus.ACTIVE).build());

            Roles savedRole = rolesRepository.findByRole("ADMIN")
                    .orElseGet(() -> rolesRepository.save(Roles.builder().role("ADMIN").build()));

            Users user = usersRepository.save(Users.builder()
                    .username("anna.verdi").passwordHash("securepass").manager(manager).role(savedRole).build());

            ContractHistoryDTO history1 = new ContractHistoryDTO(null, contract.getId(), user.getId(),
                    LocalDateTime.now().minusDays(2), ContractStatus.ACTIVE, ContractStatus.EXPIRED);
            ContractHistoryDTO history2 = new ContractHistoryDTO(null, contract.getId(), user.getId(),
                    LocalDateTime.now(), ContractStatus.EXPIRED, ContractStatus.CANCELLED);

            mockMvc.perform(post("/contract-history")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(history1)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/contract-history")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(history2)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/contract-history/contract/{contractId}", contract.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        /**
         * Tests deleting a contract history record.
         */
        @Test
        @Order(4)
        @DisplayName("The contract history has been deleted successfully")
        @WithMockUser(username = "giorgio.neri", roles = "ADMIN")
        void shouldDeleteContractHistory() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Giorgio").lastName("Neri").email("giorgio.neri@example.com").department("Finance").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Finance Area").description("Finance operations").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Finance Corp").contractNumber("CH004").projectName("Finance Project")
                    .businessArea(area).manager(manager)
                    .startDate(LocalDateTime.now().toLocalDate()).endDate(LocalDateTime.now().plusMonths(12).toLocalDate())
                    .status(ContractStatus.ACTIVE).build());

            Roles savedRole = rolesRepository.findByRole("ADMIN")
                    .orElseGet(() -> rolesRepository.save(Roles.builder().role("ADMIN").build()));

            Users user = usersRepository.save(Users.builder()
                    .username("giorgio.neri").passwordHash("passwordfinance").manager(manager).role(savedRole).build());

            ContractHistoryDTO history = new ContractHistoryDTO(null, contract.getId(), user.getId(),
                    LocalDateTime.now(), ContractStatus.ACTIVE, ContractStatus.CANCELLED);

            String response = mockMvc.perform(post("/contract-history")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(history)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long id = objectMapper.readTree(response).get("id").asLong();

            mockMvc.perform(delete("/contract-history/{id}", id))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/contract-history/{id}", id))
                    .andExpect(status().isNotFound());
        }
        
        /**
         * Tests retrieving all contract history records.
         */
        @Test
        @Order(5)
        @DisplayName("All contract histories have been retrieved successfully")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldGetAllContractHistories() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                .firstName("Test").lastName("User").email("test@example.com").department("TestDept").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                .name("Test Area").description("Test").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                .customerName("Test Client").contractNumber("CH999").wbsCode("WBS999")
                .projectName("Test Project").businessArea(area).manager(manager)
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(10))
                .status(ContractStatus.ACTIVE).build());

            Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());

            Users user = usersRepository.save(Users.builder()
                .username("admin").passwordHash("pwd").manager(manager).role(role).build());

            ContractHistoryDTO dto = new ContractHistoryDTO(null, contract.getId(), user.getId(),
                LocalDateTime.now(), ContractStatus.ACTIVE, ContractStatus.CANCELLED);

            mockMvc.perform(post("/contract-history")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

            mockMvc.perform(get("/contract-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        }
    }
}
