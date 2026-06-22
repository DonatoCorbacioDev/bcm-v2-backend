package com.donatodev.bcm_backend.controller;

import static org.hamcrest.Matchers.containsString;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.auth.RefreshCookieFactory;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrganizationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private ManagersRepository managersRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private static final String REGISTER_URL = "/organizations/register";
    private static final String ME_URL = "/organizations/me";

    private static final String VALID_REGISTRATION = """
            {
              "organizationName": "Test Corp",
              "adminUsername":    "testadmin",
              "adminPassword":    "password123",
              "adminEmail":       "admin@testcorp.com",
              "adminFirstName":   "Test",
              "adminLastName":    "Admin"
            }
            """;

    @BeforeEach
    void setup() {
        refreshTokenRepository.deleteAll();
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();
        organizationRepository.deleteAll();
        rolesRepository.save(Roles.builder().role("ADMIN").build());
        rolesRepository.save(Roles.builder().role("MANAGER").build());
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("POST /organizations/register")
    @SuppressWarnings("unused")
    class Register {

        @Test
        @Order(1)
        @DisplayName("Successful registration returns 201 with access token and refresh cookie")
        void shouldRegisterSuccessfully() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REGISTRATION))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andExpect(cookie().exists(RefreshCookieFactory.COOKIE_NAME))
                    .andExpect(cookie().httpOnly(RefreshCookieFactory.COOKIE_NAME, true));
        }

        @Test
        @Order(2)
        @DisplayName("Duplicate username returns 400")
        void shouldRejectDuplicateUsername() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REGISTRATION))
                    .andExpect(status().isCreated());

            String duplicate = """
                    {
                      "organizationName": "Another Corp",
                      "adminUsername":    "testadmin",
                      "adminPassword":    "password123",
                      "adminEmail":       "other@corp.com",
                      "adminFirstName":   "Other",
                      "adminLastName":    "Admin"
                    }
                    """;
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(duplicate))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Username already exists")));
        }

        @Test
        @Order(3)
        @DisplayName("Duplicate email returns 400")
        void shouldRejectDuplicateEmail() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REGISTRATION))
                    .andExpect(status().isCreated());

            String duplicate = """
                    {
                      "organizationName": "Another Corp",
                      "adminUsername":    "otheradmin",
                      "adminPassword":    "password123",
                      "adminEmail":       "admin@testcorp.com",
                      "adminFirstName":   "Other",
                      "adminLastName":    "Admin"
                    }
                    """;
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(duplicate))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Email already in use")));
        }

        @Test
        @Order(4)
        @DisplayName("Blank required fields return 400 validation error")
        void shouldRejectBlankFields() throws Exception {
            String invalid = """
                    {
                      "organizationName": "",
                      "adminUsername":    "admin",
                      "adminPassword":    "password123",
                      "adminEmail":       "admin@corp.com",
                      "adminFirstName":   "A",
                      "adminLastName":    "B"
                    }
                    """;
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalid))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(5)
        @DisplayName("Invalid email format returns 400")
        void shouldRejectInvalidEmail() throws Exception {
            String invalid = """
                    {
                      "organizationName": "Corp",
                      "adminUsername":    "admin",
                      "adminPassword":    "password123",
                      "adminEmail":       "not-an-email",
                      "adminFirstName":   "A",
                      "adminLastName":    "B"
                    }
                    """;
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalid))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("GET /organizations/me")
    @SuppressWarnings("unused")
    class GetMe {

        private void seedAdminUser() {
            Organization org = organizationRepository.save(
                    Organization.builder().name("My Corp").slug("my-corp")
                            .subscriptionTier(SubscriptionTier.FREE).build());
            Managers manager = managersRepository.save(
                    Managers.builder().firstName("Admin").lastName("User")
                            .email("admin@mycorp.com").organization(org).build());
            Roles role = rolesRepository.findByRole("ADMIN").orElseThrow();
            usersRepository.save(Users.builder()
                    .username("org-admin").passwordHash("pwd").verified(true)
                    .role(role).manager(manager).organization(org).build());
        }

        @Test
        @Order(1)
        @WithMockUser(username = "org-admin", roles = "ADMIN")
        @DisplayName("Admin retrieves their organization profile")
        void shouldReturnOrgProfile() throws Exception {
            seedAdminUser();
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("My Corp"))
                    .andExpect(jsonPath("$.slug").value("my-corp"))
                    .andExpect(jsonPath("$.subscriptionTier").value("FREE"));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager is denied access")
        void shouldReturn403ForManager() throws Exception {
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(3)
        @DisplayName("Unauthenticated request returns 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Order(4)
        @WithMockUser(username = "no-org-user", roles = "ADMIN")
        @DisplayName("Admin without organization returns 404")
        void shouldReturn404WhenUserHasNoOrg() throws Exception {
            Roles role = rolesRepository.findByRole("ADMIN").orElseThrow();
            usersRepository.save(Users.builder()
                    .username("no-org-user").passwordHash("pwd").verified(true)
                    .role(role).build());
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("PUT /organizations/me")
    @SuppressWarnings("unused")
    class UpdateMe {

        private void seedAdminUser() {
            Organization org = organizationRepository.save(
                    Organization.builder().name("Old Name").slug("old-name")
                            .subscriptionTier(SubscriptionTier.FREE).build());
            Managers manager = managersRepository.save(
                    Managers.builder().firstName("Admin").lastName("User")
                            .email("admin@update.com").organization(org).build());
            Roles role = rolesRepository.findByRole("ADMIN").orElseThrow();
            usersRepository.save(Users.builder()
                    .username("update-admin").passwordHash("pwd").verified(true)
                    .role(role).manager(manager).organization(org).build());
        }

        @Test
        @Order(1)
        @WithMockUser(username = "update-admin", roles = "ADMIN")
        @DisplayName("Admin updates organization name and tier")
        void shouldUpdateOrgNameAndTier() throws Exception {
            seedAdminUser();
            mockMvc.perform(put(ME_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"New Name\",\"subscriptionTier\":\"PRO\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Name"))
                    .andExpect(jsonPath("$.subscriptionTier").value("PRO"));
        }

        @Test
        @Order(2)
        @WithMockUser(username = "update-admin", roles = "ADMIN")
        @DisplayName("Partial update (only name) leaves tier unchanged")
        void shouldUpdateOnlyName() throws Exception {
            seedAdminUser();
            mockMvc.perform(put(ME_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Renamed Corp\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Renamed Corp"))
                    .andExpect(jsonPath("$.subscriptionTier").value("FREE"));
        }

        @Test
        @Order(3)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager is denied update access")
        void shouldReturn403ForManager() throws Exception {
            mockMvc.perform(put(ME_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Hack\"}"))
                    .andExpect(status().isForbidden());
        }
    }
}
