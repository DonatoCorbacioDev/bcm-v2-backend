package com.donatodev.bcm_backend.controller;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import com.donatodev.bcm_backend.support.TestTenant;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.entity.Organization;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import com.donatodev.bcm_backend.dto.FinancialTypeDTO;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link com.donatodev.bcm_backend.controller.FinancialTypeController}.
 * <p>
 * This test class verifies the REST API endpoints related to {@code FinancialTypes},
 * covering full CRUD operations and error handling.
 * </p>
 * 
 * <p>
 * Tests are executed with {@code @WithMockUser(roles = "ADMIN")} to simulate 
 * an authenticated user with administrative privileges.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinancialTypeControllerTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    /** The organization this class's requests and fixtures belong to, as a real token's would. */
    private Organization testOrg;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FinancialTypesRepository repository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    /**
     * Cleans the test database before each test execution to ensure test isolation.
     */
    @AfterEach
    void leaveTestTenant() {
        TestTenant.clear();
    }

    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDb() {
        testDataCleaner.clean();
        testOrg = organizationRepository.save(Organization.builder()
                .name("Test org").slug("test-org-" + java.util.UUID.randomUUID()).build());
        TestTenant.use(testOrg.getId());
    }

    /**
     * Nested test class for grouped verification of all API operations on financial types.
     */
    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("API Verification on Financial Type")
    @SuppressWarnings("unused")
    class VerificationApiFinancialType {

        /**
         * Tests the creation of a new financial type via POST request.
         * 
         * @throws Exception in case of request failure
         */
        @Test
        @Order(1)
        @DisplayName("The new financial type has been created")
        @WithMockUser(roles = "ADMIN")
        void shouldCreateFinancialType() throws Exception {
            FinancialTypeDTO dto = new FinancialTypeDTO(
                    null,
                    "CAPEX",
                    "Capital Expenditure",
                    FinancialCategory.COST);

            mockMvc.perform(post("/financial-types")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("CAPEX"))
                    .andExpect(jsonPath("$.description").value("Capital Expenditure"));
        }

        /**
         * Tests retrieving all financial types via GET request.
         * 
         * @throws Exception in case of request failure
         */
        @Test
        @Order(2)
        @DisplayName("All financial types have been recovered successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetAllFinancialTypes() throws Exception {
            repository.saveAll(List.of(
                    FinancialTypes.builder().organization(testOrg).name("OPEX").description("Operational Expenditure").category(FinancialCategory.COST).build(),
                    FinancialTypes.builder().organization(testOrg).name("INVESTMENT").description("Investment Type").category(FinancialCategory.COST).build()
            ));

            mockMvc.perform(get("/financial-types"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[?(@.name=='OPEX')]").exists())
                    .andExpect(jsonPath("$[?(@.name=='INVESTMENT')]").exists());
        }

        /**
         * Tests retrieving a financial type by its ID.
         * 
         * @throws Exception in case of request failure
         */
        @Test
        @Order(3)
        @DisplayName("The financial type with given ID has been retrieved successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetFinancialTypeById() throws Exception {
            FinancialTypes savedEntity = repository.save(
                    FinancialTypes.builder().organization(testOrg)
                            .name("R&D")
                            .description("Research and Development")
                            .category(FinancialCategory.COST)
                            .build());

            mockMvc.perform(get("/financial-types/{id}", savedEntity.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(savedEntity.getId().toString()))
                    .andExpect(jsonPath("$.name").value("R&D"))
                    .andExpect(jsonPath("$.description").value("Research and Development"));
        }

        /**
         * Tests updating an existing financial type via PUT request.
         * 
         * @throws Exception in case of request failure
         */
        @Test
        @Order(4)
        @DisplayName("The financial type has been updated successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldUpdateFinancialType() throws Exception {
            FinancialTypes original = repository.save(
                    FinancialTypes.builder().organization(testOrg)
                            .name("OLD_TYPE")
                            .description("Old description")
                            .category(FinancialCategory.COST)
                            .build());

            FinancialTypeDTO updatedDTO = new FinancialTypeDTO(
                    original.getId(),
                    "NEW_TYPE",
                    "Updated description",
                    FinancialCategory.REVENUE);

            mockMvc.perform(put("/financial-types/{id}", original.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updatedDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("NEW_TYPE"))
                    .andExpect(jsonPath("$.description").value("Updated description"));
        }

        /**
         * Tests deleting a financial type by ID.
         * 
         * @throws Exception in case of request failure
         */
        @Test
        @Order(5)
        @DisplayName("The financial type has been deleted successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldDeleteFinancialType() throws Exception {
            FinancialTypes typeToDelete = repository.save(
                    FinancialTypes.builder().organization(testOrg)
                            .name("DELETE_TYPE")
                            .description("Temporary type")
                            .category(FinancialCategory.COST)
                            .build());

            mockMvc.perform(delete("/financial-types/{id}", typeToDelete.getId()))
                    .andExpect(status().isNoContent());
        }

        /**
         * Tests retrieving a non-existent financial type, expecting a 404 status.
         * 
         * @throws Exception in case of request failure
         */
        @Test
        @Order(6)
        @DisplayName("Financial type not found when searching by invalid ID")
        @WithMockUser(roles = "ADMIN")
        void shouldReturnNotFoundForInvalidId() throws Exception {
            mockMvc.perform(get("/financial-types/{id}", uid(9999)))
                    .andExpect(status().isNotFound());
        }
    }
}
