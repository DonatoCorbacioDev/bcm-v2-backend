package com.donatodev.bcm_backend.controller;

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

import com.donatodev.bcm_backend.dto.RoleDTO;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link com.donatodev.bcm_backend.controller.RoleController}.
 * <p>
 * This test class verifies the correctness of the REST API endpoints related to {@code Roles},
 * which are read-only (see {@link RoleController}), and their error handling.
 * </p>
 * <p>
 * All tests are executed with a test profile and in-memory H2 database,
 * and the database is cleaned before each test using {@link TestDataCleaner}.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RolesRepository repository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    /**
     * Cleans the database before each test run to ensure isolation and consistency.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDb() {
        testDataCleaner.clean();
    }

    /**
     * Nested test class grouping all role-related API tests.
     */
    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("API Verification on Role")
    @SuppressWarnings("unused")
    class VerificationApiRole {

        /**
         * Tests retrieving all roles via GET request.
         */
        @Test
        @Order(2)
        @DisplayName("All roles have been recovered successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetAllRoles() throws Exception {
            repository.saveAll(List.of(
                Roles.builder().role("ADMIN").build(),
                Roles.builder().role("EMPLOYEE").build(),
                Roles.builder().role("SUPERVISOR").build()
            ));

            mockMvc.perform(get("/roles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[?(@.role=='ADMIN')]").exists())
                    .andExpect(jsonPath("$[?(@.role=='EMPLOYEE')]").exists())
                    .andExpect(jsonPath("$[?(@.role=='SUPERVISOR')]").exists());
        }

        /**
         * Roles are shared by every organization: no admin may create,
         * rename or delete one (renaming MANAGER to ADMIN would promote every
         * manager of every organization).
         */
        @Test
        @Order(4)
        @DisplayName("Roles can't be created, renamed or deleted through the API")
        @WithMockUser(roles = "ADMIN")
        void rolesAreReadOnly() throws Exception {
            Roles manager = repository.save(Roles.builder().role("MANAGER").build());
            String rename = objectMapper.writeValueAsString(new RoleDTO(manager.getId(), "ADMIN"));

            mockMvc.perform(post("/roles").contentType(MediaType.APPLICATION_JSON).content(rename))
                    .andExpect(status().isMethodNotAllowed());
            // No single role is exposed at all (OWASP API9): nothing at /roles/{id} to change.
            mockMvc.perform(put("/roles/{id}", manager.getId()).contentType(MediaType.APPLICATION_JSON).content(rename))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/roles/{id}", manager.getId()))
                    .andExpect(status().isNotFound());

            org.junit.jupiter.api.Assertions.assertEquals(1, repository.count());
            org.junit.jupiter.api.Assertions.assertEquals("MANAGER",
                    repository.findById(manager.getId()).orElseThrow().getRole());
        }

        /**
         * Tests unauthorized access when no authentication is provided.
         */
        @Test
        @DisplayName("Access denied without authentication")
        void shouldReturn401WhenUnauthorized() throws Exception {
            mockMvc.perform(get("/roles"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
