package com.donatodev.bcm_backend.controller;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import com.donatodev.bcm_backend.support.TestTenant;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.entity.Organization;
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
import tools.jackson.databind.ObjectMapper;

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
    private OrganizationRepository organizationRepository;

    /** The organization this class's requests and fixtures belong to, as a real token's would. */
    private Organization testOrg;

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
                    Managers.builder().organization(testOrg)
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
                    role.getId(),
                    null,
                    null
            );

            mockMvc.perform(post("/users")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("mario.rossi"));
        }

        @Test
        @Order(1)
        @DisplayName("Creating a user with a 6-character password is refused with the rule's message")
        @WithMockUser(roles = "ADMIN")
        void shouldRefuseCreateUserWithShortPassword() throws Exception {
            Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());
            UserDTO dto = new UserDTO(null, "short.pwd", "abc123", null, role.getId(), null, null);

            mockMvc.perform(post("/users")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("almeno 8 caratteri")));
        }

        @Test
        @Order(1)
        @DisplayName("A new own password over 72 bytes is a 400 with a clear message, not a BCrypt error")
        @WithMockUser(username = "anyone")
        void shouldRefuseOwnPasswordOverBcryptLimit() throws Exception {
            String body = "{\"currentPassword\":\"whatever1\",\"newPassword\":\"" + "x".repeat(100) + "\"}";

            mockMvc.perform(post("/users/me/password")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("troppo lunga")));
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
                    Managers.builder().organization(testOrg)
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
                    Users.builder().organization(testOrg)
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
                    Managers.builder().organization(testOrg)
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
                    Users.builder().organization(testOrg)
                            .username("laura.bianchi")
                            .passwordHash("password")
                            .manager(manager)
                            .role(role)
                            .build());

            mockMvc.perform(get("/users/{id}", user.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(user.getId().toString()))
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
                    Managers.builder().organization(testOrg)
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
                    Users.builder().organization(testOrg)
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
                    role.getId(),
                    null,
                    null
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
                    Managers.builder().organization(testOrg)
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
                    Users.builder().organization(testOrg)
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
            mockMvc.perform(get("/users/{id}", uid(9999)))
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
                    Managers.builder().organization(testOrg)
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
                    "{\"username\":\"newuser@example.com\",\"role\":\"%s\",\"managerId\":\"%s\"}",
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
                    Managers.builder().organization(testOrg)
                            .firstName("Patch")
                            .lastName("Manager")
                            .email("patch@example.com")
                            .department("Dev")
                            .build());

            // Only ADMIN and MANAGER exist, and the request's role is now checked against them.
            Roles role = rolesRepository.findByRole("MANAGER").orElseGet(() -> rolesRepository.save(
                    Roles.builder()
                            .role("MANAGER")
                            .build()));

            Users existingUser = usersRepository.save(
                    Users.builder().organization(testOrg)
                            .username("patch.user")
                            .passwordHash("oldpass")
                            .manager(manager)
                            .role(role)
                            .verified(true)
                            .build());

            String patchRequest = String.format(
                    "{\"username\":\"updated.user\",\"role\":\"%s\",\"managerId\":\"%s\"}",
                    role.getRole(), manager.getId()
            );

            mockMvc.perform(patch("/users/{id}", existingUser.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(patchRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("updated.user"));
        }

    }
}
