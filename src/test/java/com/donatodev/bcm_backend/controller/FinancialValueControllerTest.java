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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.FinancialValueDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for
 * {@link com.donatodev.bcm_backend.controller.FinancialValueController}.
 * <p>
 * This test class verifies the REST API endpoints related to
 * {@code FinancialValues}, ensuring correct functionality of CRUD operations
 * and response validation.
 * </p>
 * <p>
 * Each test is executed in an isolated environment using an in-memory test
 * database. The {@link TestDataCleaner} utility is used to reset the state
 * between tests.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinancialValueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BusinessAreasRepository businessAreasRepository;

    @Autowired
    private FinancialTypesRepository financialTypesRepository;

    @Autowired
    private ContractsRepository contractsRepository;

    @Autowired
    private FinancialValuesRepository financialValuesRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    /**
     * Clears the test database before each test run.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDb() {
        testDataCleaner.clean();
    }

    /**
     * Utility method to create and persist an ADMIN user associated with a
     * manager.
     *
     * @param manager the manager to associate with the user
     * @return the persisted user entity
     */
    private Users createAdminWithManager(Managers manager) {
        Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());
        return usersRepository.save(Users.builder()
                .username("admin")
                .passwordHash("admin")
                .role(role)
                .manager(manager)
                .verified(true)
                .build());
    }

    /**
     * Utility method to create and persist a manager entity.
     *
     * @return the persisted manager
     */
    private Managers createManager() {
        return managersRepository.save(Managers.builder()
                .firstName("Test")
                .lastName("Manager")
                .email("test.manager." + System.currentTimeMillis() + "@example.com")
                .department("TestDept")
                .build());
    }

    /**
     * Utility method to create and persist a contract.
     *
     * @param area the business area
     * @param manager the manager
     * @return the persisted contract
     */
    private Contracts createContract(BusinessAreas area, Managers manager) {
        return contractsRepository.save(Contracts.builder()
                .customerName("Customer " + System.currentTimeMillis())
                .contractNumber("CONTRACT-" + System.currentTimeMillis())
                .businessArea(area)
                .manager(manager)
                .startDate(LocalDate.now())
                .status(ContractStatus.ACTIVE)
                .build());
    }

    /**
     * Nested test class to verify the REST API for financial values.
     */
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("API Verification on FinancialValues")
    @SuppressWarnings("unused")
    class VerificationApiFinancialValues {

        /**
         * Tests creating a new financial value.
         */
        @Test
        @Order(1)
        @DisplayName("The new financial value has been created")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldCreateFinancialValue() throws Exception {
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Technology")
                    .description("IT Solutions")
                    .build());

            FinancialTypes type = financialTypesRepository.save(FinancialTypes.builder()
                    .name("SALES")
                    .description("Sales Revenue")
                    .build());

            Managers manager = createManager();
            Contracts contract = createContract(area, manager);
            createAdminWithManager(manager);

            FinancialValueDTO dto = new FinancialValueDTO(null, 5, 2025, 10000.00,
                    type.getId(), area.getId(), contract.getId(), type.getName(), area.getName(), contract.getCustomerName());

            mockMvc.perform(post("/financial-values")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.financialAmount").value(10000.00))
                    .andExpect(jsonPath("$.month").value(5))
                    .andExpect(jsonPath("$.year").value(2025));
        }

        /**
         * Tests retrieving all financial values.
         */
        @Test
        @Order(2)
        @DisplayName("All financial values have been recovered successfully")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldGetAllFinancialValues() throws Exception {
            shouldCreateFinancialValue();
            mockMvc.perform(get("/financial-values"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        /**
         * Tests retrieving a financial value by ID.
         */
        @Test
        @Order(3)
        @DisplayName("The financial value with given ID has been retrieved successfully")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldGetFinancialValueById() throws Exception {
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Marketing")
                    .description("Marketing Department")
                    .build());

            FinancialTypes type = financialTypesRepository.save(FinancialTypes.builder()
                    .name("COSTS")
                    .description("Operational Costs")
                    .build());

            Managers manager = createManager();
            Contracts contract = createContract(area, manager);
            createAdminWithManager(manager);

            FinancialValues value = financialValuesRepository.save(FinancialValues.builder()
                    .month(6)
                    .year(2025)
                    .financialAmount(5000.00)
                    .financialType(type)
                    .businessArea(area)
                    .contract(contract)
                    .build());

            mockMvc.perform(get("/financial-values/{id}", value.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(value.getId()))
                    .andExpect(jsonPath("$.financialAmount").value(5000.00))
                    .andExpect(jsonPath("$.month").value(6))
                    .andExpect(jsonPath("$.year").value(2025));
        }

        /**
         * Tests updating an existing financial value.
         */
        @Test
        @Order(4)
        @DisplayName("The financial value has been updated successfully")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldUpdateFinancialValue() throws Exception {
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Finance")
                    .description("Financial Department")
                    .build());

            FinancialTypes type = financialTypesRepository.save(FinancialTypes.builder()
                    .name("INVESTMENTS")
                    .description("Capital Investments")
                    .build());

            Managers manager = createManager();
            Contracts contract = createContract(area, manager);
            createAdminWithManager(manager);

            FinancialValues original = financialValuesRepository.save(FinancialValues.builder()
                    .month(7)
                    .year(2025)
                    .financialAmount(2000.00)
                    .financialType(type)
                    .businessArea(area)
                    .contract(contract)
                    .build());

            FinancialValueDTO updatedDTO = new FinancialValueDTO(original.getId(), 8, 2025, 3000.00,
                    type.getId(), area.getId(), contract.getId(), type.getName(), area.getName(), contract.getCustomerName());

            mockMvc.perform(put("/financial-values/{id}", original.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updatedDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(original.getId()))
                    .andExpect(jsonPath("$.financialAmount").value(3000.00))
                    .andExpect(jsonPath("$.month").value(8))
                    .andExpect(jsonPath("$.year").value(2025));
        }

        /**
         * Tests deleting a financial value by ID.
         */
        @Test
        @Order(5)
        @DisplayName("The financial value has been deleted successfully")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldDeleteFinancialValue() throws Exception {
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Operations")
                    .description("Operational Activities")
                    .build());

            FinancialTypes type = financialTypesRepository.save(FinancialTypes.builder()
                    .name("EXPENSES")
                    .description("Monthly Expenses")
                    .build());

            Managers manager = createManager();
            Contracts contract = createContract(area, manager);
            createAdminWithManager(manager);

            FinancialValues toDelete = financialValuesRepository.save(FinancialValues.builder()
                    .month(9)
                    .year(2025)
                    .financialAmount(1500.00)
                    .financialType(type)
                    .businessArea(area)
                    .contract(contract)
                    .build());

            mockMvc.perform(delete("/financial-values/{id}", toDelete.getId()))
                    .andExpect(status().isNoContent());
        }

        /**
         * Tests error handling when retrieving a financial value by a
         * non-existent ID.
         */
        @Test
        @Order(6)
        @DisplayName("Financial value not found when searching by invalid ID")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldReturnNotFoundForInvalidId() throws Exception {
            mockMvc.perform(get("/financial-values/{id}", 9999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Order(7)
        @DisplayName("Get financial values by contract ID")
        @WithMockUser(roles = "ADMIN")
        void shouldGetValuesByContract() throws Exception {
            // Create area and type FIRST
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Sales")
                    .description("Sales Department")
                    .build());

            FinancialTypes type = financialTypesRepository.save(FinancialTypes.builder()
                    .name("Revenue")
                    .description("Revenue Type")
                    .build());

            // Create manager (needed for contract)
            Managers manager = createManager();

            // Create contract with area and manager
            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Client")
                    .contractNumber("C123")
                    .wbsCode("WBS")
                    .projectName("Project")
                    .status(ContractStatus.ACTIVE)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusDays(30))
                    .businessArea(area)
                    .manager(manager)
                    .build());

            // Create financial value
            FinancialValues value = financialValuesRepository.save(FinancialValues.builder()
                    .contract(contract)
                    .financialType(type)
                    .businessArea(area)
                    .month(1)
                    .year(2025)
                    .financialAmount(1000.00)
                    .build());

            // Test the endpoint
            mockMvc.perform(get("/financial-values/by-contract/{contractId}", contract.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].financialAmount").value(1000.00));
        }
    }
}
