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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.ManagerDTO;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for
 * {@link com.donatodev.bcm_backend.controller.ManagerController}.
 * <p>
 * This class verifies the behavior of the REST endpoints related to the
 * {@code Managers} entity, including creation, retrieval, update, and deletion.
 * </p>
 * <p>
 * Each test runs in isolation using a test profile and in-memory database, with
 * the {@link TestDataCleaner} utility clearing all data before each execution.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagerControllerTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    /** The organization this class's requests and fixtures belong to, as a real token's would. */
    private Organization testOrg;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ManagersRepository repository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    /**
     * Cleans the database before each test case to ensure test isolation.
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
                .name("Test org").slug("test-org-" + UUID.randomUUID()).build());
        TestTenant.use(testOrg.getId());
    }

    /**
     * Nested class containing all tests related to the Manager API.
     */
    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("API Verification on Manager")
    @SuppressWarnings("unused")
    class VerificationApiManager {

        /**
         * Tests creating a new manager entity.
         *
         * @throws Exception if the request fails
         */
        @Test
        @Order(1)
        @DisplayName("The new manager has been created")
        @WithMockUser(roles = "ADMIN")
        void shouldCreateManager() throws Exception {
            ManagerDTO dto = new ManagerDTO(null, "Francesco", "Neri", "francesco.neri@example.com", "1234567890", "IT");

            mockMvc.perform(post("/managers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.firstName").value("Francesco"));
        }

        /**
         * Tests retrieving all manager records.
         *
         * @throws Exception if the request fails
         */
        @Test
        @Order(2)
        @DisplayName("All managers have been recovered successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetAllManagers() throws Exception {
            repository.saveAll(List.of(
                    Managers.builder().organization(testOrg).firstName("Marco").lastName("Bianchi").email("marco@example.com").phoneNumber("123").department("Finance").build(),
                    Managers.builder().organization(testOrg).firstName("Lucia").lastName("Verdi").email("lucia@example.com").phoneNumber("456").department("HR").build()
            ));

            mockMvc.perform(get("/managers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[?(@.firstName=='Marco')]").exists())
                    .andExpect(jsonPath("$[?(@.firstName=='Lucia')]").exists());
        }

        /**
         * Tests retrieving a manager by ID.
         *
         * @throws Exception if the request fails
         */
        @Test
        @Order(3)
        @DisplayName("The manager with given ID has been retrieved successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetManagerById() throws Exception {
            Managers m = repository.save(
                    Managers.builder().organization(testOrg).firstName("Lucio").lastName("Gialli").email("lucio@example.com").phoneNumber("789").department("HR").build()
            );

            mockMvc.perform(get("/managers/{id}", m.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(m.getId().toString()))
                    .andExpect(jsonPath("$.firstName").value("Lucio"));
        }

        /**
         * Tests updating an existing manager.
         *
         * @throws Exception if the request fails
         */
        @Test
        @Order(4)
        @DisplayName("The manager has been updated successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldUpdateManager() throws Exception {
            Managers original = repository.save(
                    Managers.builder().organization(testOrg).firstName("Paola").lastName("Rossi").email("paola@example.com").phoneNumber("000").department("Finance").build()
            );

            ManagerDTO updatedDTO = new ManagerDTO(original.getId(), "Marco", "Verdi", "marco@example.com", "999", "Marketing");

            mockMvc.perform(put("/managers/{id}", original.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updatedDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Marco"))
                    .andExpect(jsonPath("$.lastName").value("Verdi"));
        }

        /**
         * Tests deleting a manager by ID.
         *
         * @throws Exception if the request fails
         */
        @Test
        @Order(5)
        @DisplayName("The manager has been deleted successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldDeleteManager() throws Exception {
            Managers m = repository.save(
                    Managers.builder().organization(testOrg).firstName("Andrea").lastName("Bianchi").email("andrea@example.com").phoneNumber("777").department("HR").build()
            );

            mockMvc.perform(delete("/managers/{id}", m.getId()))
                    .andExpect(status().isNoContent());
        }

        /**
         * Tests behavior when trying to retrieve a non-existing manager.
         *
         * @throws Exception if the request fails
         */
        @Test
        @Order(6)
        @DisplayName("Manager not found when searching by invalid ID")
        @WithMockUser(roles = "ADMIN")
        void shouldReturnNotFoundForInvalidId() throws Exception {
            mockMvc.perform(get("/managers/{id}", uid(9999)))
                    .andExpect(status().isNotFound());
        }

        /**
         * Test patching/updating a manager using PATCH method.
         */
        @Test
        @Order(9)
        @DisplayName("The manager has been patched successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldPatchManager() throws Exception {
            Managers original = repository.save(
                    Managers.builder().organization(testOrg).firstName("OldName").lastName("OldLast")
                            .email("old@example.com").phoneNumber("000").department("OldDept").build()
            );

            ManagerDTO patchedDTO = new ManagerDTO(original.getId(), "NewName", "NewLast",
                    "new@example.com", "999", "NewDept");

            mockMvc.perform(patch("/managers/{id}", original.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(patchedDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("NewName"))
                    .andExpect(jsonPath("$.lastName").value("NewLast"));
        }

        /**
         * MANAGER can list managers too — the frontend uses this endpoint to
         * populate manager pickers (e.g. assigning a "Responsabile" when
         * instantiating a contract from a template) that MANAGER users can
         * reach.
         */
        @Test
        @Order(10)
        @DisplayName("A MANAGER can list managers")
        @WithMockUser(roles = "MANAGER")
        void shouldAllowManagerRoleToListManagers() throws Exception {
            repository.save(Managers.builder().organization(testOrg).firstName("Chiara").lastName("Neri")
                    .email("chiara@example.com").phoneNumber("222").department("Ops").build());

            mockMvc.perform(get("/managers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        /**
         * MANAGER must still be denied administrative actions on managers.
         */
        @Test
        @Order(11)
        @DisplayName("A MANAGER cannot create a manager")
        @WithMockUser(roles = "MANAGER")
        void shouldDenyManagerRoleFromCreatingManagers() throws Exception {
            ManagerDTO newDTO = new ManagerDTO(null, "Blocked", "User", "blocked@example.com", "111", "IT");

            mockMvc.perform(post("/managers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(newDTO)))
                    .andExpect(status().isForbidden());
        }
    }
}
