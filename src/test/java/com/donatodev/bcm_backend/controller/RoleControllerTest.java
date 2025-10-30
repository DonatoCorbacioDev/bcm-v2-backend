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

import com.donatodev.bcm_backend.dto.RoleDTO;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link com.donatodev.bcm_backend.controller.RoleController}.
 * <p>
 * This test class verifies the correctness of the REST API endpoints related to {@code Roles},
 * including operations for create, read, update, delete, and error handling.
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
         * Tests the creation of a new role via POST request.
         */
        @Test
        @Order(1)
        @DisplayName("The new role has been created")
        @WithMockUser(roles = "ADMIN")
        void shouldCreateRole() throws Exception {
            RoleDTO dto = new RoleDTO(null, "SUPERVISOR");

            mockMvc.perform(post("/roles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("SUPERVISOR"));
        }

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
         * Tests retrieving a specific role by its ID.
         */
        @Test
        @Order(3)
        @DisplayName("The role with given ID has been retrieved successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetRoleById() throws Exception {
            Roles savedEntity = repository.save(
                    Roles.builder().role("TESTER").build()
            );

            mockMvc.perform(get("/roles/{id}", savedEntity.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(savedEntity.getId()))
                    .andExpect(jsonPath("$.role").value("TESTER"));
        }

        /**
         * Tests updating an existing role.
         */
        @Test
        @Order(4)
        @DisplayName("The role has been updated successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldUpdateRole() throws Exception {
            Roles original = repository.save(
                    Roles.builder().role("OLD_ROLE").build()
            );

            RoleDTO updatedDTO = new RoleDTO(original.getId(), "NEW_ROLE");

            mockMvc.perform(put("/roles/{id}", original.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updatedDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("NEW_ROLE"));
        }

        /**
         * Tests deleting a role by ID.
         */
        @Test
        @Order(5)
        @DisplayName("The role has been deleted successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldDeleteRole() throws Exception {
            Roles roleToDelete = repository.save(
                    Roles.builder().role("TEMP_ROLE").build()
            );

            mockMvc.perform(delete("/roles/{id}", roleToDelete.getId()))
                    .andExpect(status().isNoContent());
        }

        /**
         * Tests retrieving a non-existent role, expecting 404 Not Found.
         */
        @Test
        @Order(6)
        @DisplayName("Role not found when searching by invalid ID")
        @WithMockUser(roles = "ADMIN")
        void shouldReturnNotFoundForInvalidId() throws Exception {
            mockMvc.perform(get("/roles/{id}", 9999L))
                    .andExpect(status().isNotFound());
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
