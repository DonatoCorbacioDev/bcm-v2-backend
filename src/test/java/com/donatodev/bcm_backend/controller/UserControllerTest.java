package com.donatodev.bcm_backend.controller;

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

import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link com.donatodev.bcm_backend.controller.UserController}.
 * <p>
 * This test class verifies the behavior of the REST endpoints for {@code Users},
 * including creation, listing, retrieval by ID, update, deletion, and error handling.
 * </p>
 * <p>
 * Each test is executed in isolation using a clean in-memory database, with
 * the {@link TestDataCleaner} utility ensuring a reset before each test.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    /**
     * Cleans the test database before each test to guarantee isolation.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDb() {
        testDataCleaner.clean();
    }

    /**
     * Nested test class grouping all API tests for user management.
     */
    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("API Verification on User")
    @SuppressWarnings("unused")
    class VerificationApiUser {

        /**
         * Tests creating a new user with valid manager and role.
         */
        @Test
        @Order(1)
        @DisplayName("The new user has been created")
        @WithMockUser(roles = "ADMIN")
        void shouldCreateUser() throws Exception {
            Managers manager = managersRepository.save(
                    Managers.builder()
                            .firstName("Mario")
                            .lastName("Rossi")
                            .email("mario.rossi@example.com")
                            .department("Sales")
                            .build());

            Roles role = rolesRepository.save(
                    Roles.builder()
                            .role("ADMIN")
                            .build());

            UserDTO dto = new UserDTO(
                    null,
                    "mario.rossi",
                    "password123",
                    manager.getId(),
                    role.getId()
            );

            mockMvc.perform(post("/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("mario.rossi"));
        }

        /**
         * Tests retrieving all users in the system.
         */
        @Test
        @Order(2)
        @DisplayName("All users have been recovered successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetAllUsers() throws Exception {
            Managers manager = managersRepository.save(
                    Managers.builder()
                            .firstName("Lucia")
                            .lastName("Ferrari")
                            .email("lucia.ferrari@example.com")
                            .department("HR")
                            .build());

            Roles role = rolesRepository.save(
                    Roles.builder()
                            .role("EMPLOYEE")
                            .build());

            usersRepository.save(
                    Users.builder()
                            .username("lucia.ferrari")
                            .passwordHash("hashedpw")
                            .manager(manager)
                            .role(role)
                            .build());

            mockMvc.perform(get("/users"))
                   .andExpect(status().isOk())
                   .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                   .andExpect(jsonPath("$.length()").value(1));
        }

        /**
         * Tests retrieving a user by their ID.
         */
        @Test
        @Order(3)
        @DisplayName("The user with given ID has been retrieved successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetUserById() throws Exception {
            Managers manager = managersRepository.save(
                    Managers.builder()
                            .firstName("Laura")
                            .lastName("Bianchi")
                            .email("laura.bianchi@example.com")
                            .department("Marketing")
                            .build());

            Roles role = rolesRepository.save(
                    Roles.builder()
                            .role("EMPLOYEE")
                            .build());

            Users user = usersRepository.save(
                    Users.builder()
                            .username("laura.bianchi")
                            .passwordHash("password")
                            .manager(manager)
                            .role(role)
                            .build());

            mockMvc.perform(get("/users/{id}", user.getId()))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.id").value(user.getId()))
                   .andExpect(jsonPath("$.username").value("laura.bianchi"));
        }

        /**
         * Tests updating an existing user's information.
         */
        @Test
        @Order(4)
        @DisplayName("The user has been updated successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldUpdateUser() throws Exception {
            Managers manager = managersRepository.save(
                    Managers.builder()
                            .firstName("Giovanni")
                            .lastName("Verdi")
                            .email("giovanni.verdi@example.com")
                            .department("Finance")
                            .build());

            Roles role = rolesRepository.save(
                    Roles.builder()
                            .role("SUPERVISOR")
                            .build());

            Users originalUser = usersRepository.save(
                    Users.builder()
                            .username("giovanni.verdi")
                            .passwordHash("password")
                            .manager(manager)
                            .role(role)
                            .build());

            UserDTO updatedDTO = new UserDTO(
                    originalUser.getId(),
                    "giovanni.verdi.new",
                    "newpassword",
                    manager.getId(),
                    role.getId()
            );

            mockMvc.perform(put("/users/{id}", originalUser.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updatedDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("giovanni.verdi.new"));
        }

        /**
         * Tests deleting a user by ID.
         */
        @Test
        @Order(5)
        @DisplayName("The user has been deleted successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldDeleteUser() throws Exception {
            Managers manager = managersRepository.save(
                    Managers.builder()
                            .firstName("Franco")
                            .lastName("Neri")
                            .email("franco.neri@example.com")
                            .department("Legal")
                            .build());

            Roles role = rolesRepository.save(
                    Roles.builder()
                            .role("GUEST")
                            .build());

            Users userToDelete = usersRepository.save(
                    Users.builder()
                            .username("franco.neri")
                            .passwordHash("password")
                            .manager(manager)
                            .role(role)
                            .build());

            mockMvc.perform(delete("/users/{id}", userToDelete.getId()))
                   .andExpect(status().isNoContent());
        }

        /**
         * Tests retrieving a user by an invalid ID (expecting 404).
         */
        @Test
        @Order(6)
        @DisplayName("User not found when searching by invalid ID")
        @WithMockUser(roles = "ADMIN")
        void shouldReturnNotFoundForInvalidId() throws Exception {
            mockMvc.perform(get("/users/{id}", 9999L))
                   .andExpect(status().isNotFound());
        }
    }
}
