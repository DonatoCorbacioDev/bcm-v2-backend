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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Integration tests for
 * {@link com.donatodev.bcm_backend.controller.UserController}.
 * <p>
 * This test class verifies the behavior of the REST endpoints for
 * {@code Users}, including creation, listing, retrieval by ID, update,
 * deletion, and error handling.
 * </p>
 * <p>
 * Each test is executed in isolation using a clean in-memory database, with the
 * {@link TestDataCleaner} utility ensuring a reset before each test.
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
                    .with(csrf())
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
                    .with(csrf())
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

            mockMvc.perform(delete("/users/{id}", userToDelete.getId())
                    .with(csrf()))
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

        /**
         * Tests inviting a new user and receiving an invite link.
         */
        @Test
        @Order(7)
        @DisplayName("Should invite user and return invite link")
        @WithMockUser(roles = "ADMIN")
        void shouldInviteUser() throws Exception {
            Managers manager = managersRepository.save(
                    Managers.builder()
                            .firstName("Invite")
                            .lastName("Manager")
                            .email("invite@example.com")
                            .department("IT")
                            .build());

            Roles role = rolesRepository.save(
                    Roles.builder()
                            .role("MANAGER")
                            .build());

            String requestBody = String.format(
                    "{\"username\":\"newuser@example.com\",\"role\":\"%s\",\"managerId\":%d}",
                    role.getRole(), manager.getId()
            );

            mockMvc.perform(post("/users/invite")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inviteLink").exists())
                    .andExpect(jsonPath("$.inviteLink").isNotEmpty());
        }

        /**
         * Tests partially updating a user with PATCH.
         */
        @Test
        @Order(8)
        @DisplayName("Should partially update user via PATCH")
        @WithMockUser(roles = "ADMIN")
        void shouldPatchUser() throws Exception {
            Managers manager = managersRepository.save(
                    Managers.builder()
                            .firstName("Patch")
                            .lastName("Manager")
                            .email("patch@example.com")
                            .department("Dev")
                            .build());

            Roles role = rolesRepository.save(
                    Roles.builder()
                            .role("EMPLOYEE")
                            .build());

            Users existingUser = usersRepository.save(
                    Users.builder()
                            .username("patch.user")
                            .passwordHash("oldpass")
                            .manager(manager)
                            .role(role)
                            .verified(true)
                            .build());

            String patchRequest = String.format(
                    "{\"username\":\"updated.user\",\"role\":\"%s\",\"managerId\":%d}",
                    role.getRole(), manager.getId()
            );

            mockMvc.perform(patch("/users/{id}", existingUser.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(patchRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("updated.user"));
        }

        /**
         * Tests force resetting a user's password.
         */
        @Test
        @Order(9)
        @DisplayName("Should force reset user password")
        @WithMockUser(roles = "ADMIN")
        void shouldForceResetPassword() throws Exception {
            Managers manager = managersRepository.save(
                    Managers.builder()
                            .firstName("Reset")
                            .lastName("Manager")
                            .email("reset@example.com")
                            .department("Security")
                            .build());

            Roles role = rolesRepository.save(
                    Roles.builder()
                            .role("EMPLOYEE")
                            .build());

            Users user = usersRepository.save(
                    Users.builder()
                            .username("reset.user")
                            .passwordHash("oldpass")
                            .manager(manager)
                            .role(role)
                            .verified(true)
                            .build());

            mockMvc.perform(post("/users/{id}/force-reset", user.getId())
                    .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        /**
         * Tests searching users with pagination and filters.
         */
        @Test
        @Order(10)
        @DisplayName("Should search users with filters")
        @WithMockUser(roles = "ADMIN")
        void shouldSearchUsers() throws Exception {
            Managers manager1 = managersRepository.save(
                    Managers.builder()
                            .firstName("Search")
                            .lastName("Manager1")
                            .email("search1@example.com")
                            .department("HR")
                            .build());

            Managers manager2 = managersRepository.save(
                    Managers.builder()
                            .firstName("Search")
                            .lastName("Manager2")
                            .email("search2@example.com")
                            .department("HR")
                            .build());

            Roles role = rolesRepository.save(
                    Roles.builder()
                            .role("MANAGER")
                            .build());

            usersRepository.save(
                    Users.builder()
                            .username("search.user1")
                            .passwordHash("pass")
                            .manager(manager1) // manager1
                            .role(role)
                            .verified(true)
                            .build());

            usersRepository.save(
                    Users.builder()
                            .username("search.user2")
                            .passwordHash("pass")
                            .manager(manager2) // manager2
                            .role(role)
                            .verified(false)
                            .build());

            mockMvc.perform(get("/users/search")
                    .param("q", "search")
                    .param("role", "MANAGER")
                    .param("verified", "true")
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].username").value("search.user1"));
        }

        /**
         * Tests searching users without filters returns all.
         */
        @Test
        @Order(11)
        @DisplayName("Should search users without filters")
        @WithMockUser(roles = "ADMIN")
        void shouldSearchUsersWithoutFilters() throws Exception {
            Managers manager = managersRepository.save(
                    Managers.builder()
                            .firstName("All")
                            .lastName("Manager")
                            .email("all@example.com")
                            .department("All")
                            .build());

            Roles role = rolesRepository.save(
                    Roles.builder()
                            .role("EMPLOYEE")
                            .build());

            usersRepository.save(
                    Users.builder()
                            .username("user.one")
                            .passwordHash("pass")
                            .manager(manager)
                            .role(role)
                            .verified(true)
                            .build());

            mockMvc.perform(get("/users/search")
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }
}
