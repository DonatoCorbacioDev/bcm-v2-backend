package com.donatodev.bcm_backend.auth;

import java.util.UUID;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletResponse;

import com.donatodev.bcm_backend.dto.ForgotPasswordRequestDTO;
import com.donatodev.bcm_backend.dto.ResetPasswordRequestDTO;
import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.entity.InviteToken;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.PasswordResetToken;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.InviteTokenRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.PasswordResetTokenRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.repository.VerificationTokenRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.donatodev.bcm_backend.util.TokenHash;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for AuthController REST API endpoints.
 * <p>
 * This class tests user registration, email verification, login, password reset
 * processes, profile retrieval, error handling, validation, and authorization
 * checks. Uses MockMvc to simulate HTTP requests in a Spring Boot test context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private InviteTokenRepository inviteTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    @Autowired
    private com.donatodev.bcm_backend.repository.OrganizationRepository organizationRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.donatodev.bcm_backend.service.TotpEncryptionService totpEncryptionService;

    private UUID roleId;
    private UUID managerId;

    /**
     * Setup method executed before each test. Cleans all relevant repositories
     * and inserts a MANAGER role and a test manager.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        refreshTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        inviteTokenRepository.deleteAll();
        // Rows other test classes may leave behind (contract history, workflow
        // events...) reference users too: clear them whatever ran before.
        testDataCleaner.clean();
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();

        Roles role = rolesRepository.save(Roles.builder().role("MANAGER").build());
        Managers manager = managersRepository.save(Managers.builder()
                .firstName("Test").lastName("Manager").email("manager@test.com")
                .department("IT").phoneNumber("000111222").build());

        roleId = role.getId();
        managerId = manager.getId();
    }

    /** A verified user with the setup's manager and role, as an invitation leaves one. */
    private Users verifiedUser(String username, String password) {
        return usersRepository.save(Users.builder()
                .username(username).passwordHash(passwordEncoder.encode(password)).verified(true)
                .manager(managersRepository.findById(managerId).orElseThrow())
                .role(rolesRepository.findById(roleId).orElseThrow()).build());
    }

    /**
     * OWASP API1/API3/API5: the legacy public sign-up took a managerId and a
     * roleId from the body, so anyone could create an ADMIN inside another
     * organization, attached to one of its managers, and have the manager's
     * verification email activate it. Sign-up is /organizations/register
     * (emailed code) and users join by invitation: there is no public way to
     * create a user in an existing organization.
     */
    @Test
    @Order(1)
    void anonymousCannotCreateAnAdminInSomeoneElsesOrganization() throws Exception {
        Roles admin = rolesRepository.save(Roles.builder().role("ADMIN").build());
        UserDTO attack = new UserDTO(null, "intruder", "Intruder#2026", managerId, admin.getId(), false, null);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(attack)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/auth/verify?token=anything"))
                .andExpect(status().is4xxClientError());

        assertThat(usersRepository.findByUsername("intruder")).isEmpty();
    }

    /**
     * Test successful login after registration and verification.
     */
    @Test
    @Order(3)
    void shouldLoginSuccessfully() throws Exception {
        verifiedUser("loginuser", "mypwd123");

        AuthRequestDTO loginDto = new AuthRequestDTO("loginuser", "mypwd123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists(RefreshCookieFactory.COOKIE_NAME))
                .andExpect(cookie().httpOnly(RefreshCookieFactory.COOKIE_NAME, true));
    }

    /**
     * Test sending password reset link successfully.
     */
    @Test
    @Order(4)
    void shouldSendResetLinkSuccessfully() throws Exception {
        usersRepository.save(Users.builder()
                .username("resetme").passwordHash("pwd").verified(true)
                .manager(managersRepository.findById(managerId).orElseThrow())
                .role(rolesRepository.findById(roleId).orElseThrow()).build());

        ForgotPasswordRequestDTO req = new ForgotPasswordRequestDTO("manager@test.com");

        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("è stato inviato un link per reimpostare la password")));
    }

    /**
     * Test that an unknown email returns the same generic response, to avoid
     * leaking whether an account exists.
     */
    @Test
    @Order(27)
    void shouldReturnGenericResponseForUnknownEmail() throws Exception {
        ForgotPasswordRequestDTO req = new ForgotPasswordRequestDTO("doesnotexist@test.com");

        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("è stato inviato un link per reimpostare la password")));
    }

    /**
     * Test successful password reset using valid token.
     */
    @Test
    @Order(5)
    void shouldResetPasswordSuccessfully() throws Exception {
        Users user = usersRepository.save(Users.builder()
                .username("resetme2").passwordHash("old").verified(true)
                .manager(managersRepository.findById(managerId).orElseThrow())
                .role(rolesRepository.findById(roleId).orElseThrow()).build());

        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(TokenHash.sha256Hex("reset-token")).user(user)
                .expiryDate(LocalDateTime.of(2030, Month.JANUARY, 1, 12, 0)).build());

        ResetPasswordRequestDTO req = new ResetPasswordRequestDTO("reset-token", "newpwd123");

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Password reimpostata con successo")));
    }

    @Test
    @Order(5)
    void shouldRefuseResetToATooShortPasswordAndKeepTheToken() throws Exception {
        Users user = usersRepository.save(Users.builder()
                .username("resetme3").passwordHash("old").verified(true)
                .manager(managersRepository.findById(managerId).orElseThrow())
                .role(rolesRepository.findById(roleId).orElseThrow()).build());
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(TokenHash.sha256Hex("reset-token-short")).user(user)
                .expiryDate(LocalDateTime.of(2030, Month.JANUARY, 1, 12, 0)).build());

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ResetPasswordRequestDTO("reset-token-short", "a"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("almeno 8 caratteri")));

        // Refused before the token was used: the link still works with a valid password.
        assertEquals("old", usersRepository.findById(user.getId()).orElseThrow().getPasswordHash());
        assertTrue(passwordResetTokenRepository.findByToken(TokenHash.sha256Hex("reset-token-short")).isPresent());
    }

    /**
     * Test retrieving current authenticated user profile.
     */
    @Test
    @Order(6)
    @WithMockUser(username = "admin")
    void shouldGetCurrentUserProfile() throws Exception {
        Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());

        usersRepository.save(Users.builder()
                .username("admin").passwordHash("adminpwd").verified(true)
                .role(role).build());

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }


    /**
     * Test rejection of expired reset token.
     */
    @Test
    @Order(8)
    void shouldRejectExpiredResetToken() throws Exception {
        Users user = usersRepository.save(Users.builder()
                .username("expiredreset").passwordHash("pwd").verified(true)
                .manager(managersRepository.findById(managerId).orElseThrow())
                .role(rolesRepository.findById(roleId).orElseThrow()).build());

        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(TokenHash.sha256Hex("expired-reset-token")).user(user)
                .expiryDate(LocalDateTime.of(2020, Month.JANUARY, 1, 12, 0)).build());

        ResetPasswordRequestDTO req = new ResetPasswordRequestDTO("expired-reset-token", "newpwd123");

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Token scaduto")));
    }

    /**
     * Test failure when resetting password with invalid token.
     */
    @Test
    @Order(10)
    void shouldFailResetPasswordWithInvalidToken() throws Exception {
        ResetPasswordRequestDTO req = new ResetPasswordRequestDTO("non-existent-token", "newpwd123");

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Token non valido")));
    }

    /**
     * Test isUserAuthenticated() returns false when authentication is null.
     */
    @Test
    @Order(14)
    void shouldReturnUnauthorizedWhenAuthenticationIsExplicitlyNull() {
        SecurityContextHolder.getContext().setAuthentication(null);
        AuthController controller = new AuthController();
        boolean result = controller.isUserAuthenticated();
        assertFalse(result);
    }

    /**
     * Test unauthorized response when authentication exists but is not
     * authenticated.
     */
    @Test
    @Order(15)
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
        org.springframework.security.core.Authentication unauth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user", "pass");
        unauth.setAuthenticated(false);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(unauth);
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test unauthorized response when principal is string "anonymousUser".
     */
    @Test
    @Order(16)
    void shouldReturnUnauthorizedWhenPrincipalIsAnonymousUser() throws Exception {
        org.springframework.security.core.Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("anonymousUser", "N/A");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test unauthorized response when principal is authenticated but is
     * "anonymousUser".
     */
    @Test
    @Order(17)
    void shouldReturnUnauthorizedWhenAuthenticatedButPrincipalIsAnonymousUser() throws Exception {
        org.springframework.security.core.Authentication auth
                = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "anonymousUser", "N/A", java.util.Collections.emptyList());
        // No need to call setAuthenticated(true); it's already authenticated
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test unauthorized response when anonymous user access.
     */
    @Test
    @Order(18)
    @org.springframework.security.test.context.support.WithAnonymousUser
    void shouldReturnUnauthorizedWhenAnonymousUser() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test OK response when principal is a String username (not anonymous).
     */
    @Test
    @Order(19)
    void shouldReturnOkWhenPrincipalIsStringButNotAnonymous() throws Exception {
        Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());

        usersRepository.save(Users.builder()
                .username("notanon").passwordHash("pwd").verified(true)
                .role(role).build());

        Authentication auth = new UsernamePasswordAuthenticationToken("notanon", "pwd", java.util.Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("notanon"));
    }

    /**
     * Test OK response when principal is a custom UserDetails object.
     */
    @Test
    @Order(20)
    void shouldReturnOkWhenPrincipalIsCustomObject() throws Exception {
        Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());

        usersRepository.save(Users.builder()
                .username("customuser").passwordHash("pwd").verified(true)
                .role(role).build());

        User userDetails = new User("customuser", "pwd", Collections.emptyList());
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, "pwd", userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk());
    }

    /**
     * Test AuthenticationException when login credentials are invalid.
     */
    @Test
    @Order(21)
    void shouldHandleAuthenticationException() throws Exception {
        AuthRequestDTO invalidLogin = new AuthRequestDTO("nonexistentuser", "wrongpassword");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Non autorizzato")));
    }

    /**
     * Test successful token refresh returns a new access token.
     */
    @Test
    @Order(22)
    void shouldRefreshTokenSuccessfully() throws Exception {
        verifiedUser("refreshuser", "pass123");

        MockHttpServletResponse loginResponse = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthRequestDTO("refreshuser", "pass123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String refreshToken = loginResponse.getCookie(RefreshCookieFactory.COOKIE_NAME).getValue();

        MockHttpServletResponse refreshResponse = mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        String rotatedRefreshToken = refreshResponse.getCookie(RefreshCookieFactory.COOKIE_NAME).getValue();
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);
    }

    /** A verified user inside an organization: its tokens authenticate (the JWT filter needs the org). */
    private Users userInOrganization(String username, String password) {
        com.donatodev.bcm_backend.entity.Organization org = organizationRepository.save(
                com.donatodev.bcm_backend.entity.Organization.builder()
                        .name("Sessions org").slug("sessions-org-" + UUID.randomUUID()).build());
        return usersRepository.save(Users.builder()
                .username(username).passwordHash(passwordEncoder.encode(password)).verified(true)
                .organization(org)
                .manager(managersRepository.findById(managerId).orElseThrow())
                .role(rolesRepository.findById(roleId).orElseThrow()).build());
    }

    private MockHttpServletResponse login(String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthRequestDTO(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    /**
     * OWASP API2: changing one's own password ends every other session at once
     * (refresh revoked, earlier access tokens refused) while this one goes on
     * through the new refresh cookie of the response.
     */
    @Test
    @Order(22)
    void changingOwnPasswordEndsOtherSessionsAndKeepsThisOne() throws Exception {
        userInOrganization("sessionuser", "OldPassword1");
        MockHttpServletResponse otherDevice = login("sessionuser", "OldPassword1");
        MockHttpServletResponse thisDevice = login("sessionuser", "OldPassword1");
        String otherRefresh = otherDevice.getCookie(RefreshCookieFactory.COOKIE_NAME).getValue();
        String otherAccess = objectMapper.readTree(otherDevice.getContentAsString()).get("token").asText();
        String thisAccess = objectMapper.readTree(thisDevice.getContentAsString()).get("token").asText();
        // Tokens carry whole seconds: the change must fall in a later second than the logins.
        Thread.sleep(1100);

        MockHttpServletResponse changed = mockMvc.perform(post("/users/me/password")
                .header("Authorization", "Bearer " + thisAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"OldPassword1\",\"newPassword\":\"NewPassword2\"}"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(RefreshCookieFactory.COOKIE_NAME))
                .andReturn().getResponse();

        // The other device: its refresh token is revoked and its access token refused now.
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, otherRefresh)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + otherAccess))
                .andExpect(status().isUnauthorized());

        // This device renews through the new cookie and carries on.
        String renewed = changed.getCookie(RefreshCookieFactory.COOKIE_NAME).getValue();
        String newAccess = objectMapper.readTree(mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, renewed)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());
    }

    @Autowired
    private com.donatodev.bcm_backend.service.PasswordResetTokenService passwordResetTokenService;

    /**
     * OWASP API2: only the hash of a reset link is stored. The emailed token
     * works; what the database holds does not, so a dump or a stolen backup
     * gives nobody a usable link.
     */
    @Test
    @Order(22)
    void resetLinkWorksButWhatTheDatabaseHoldsDoesNot() throws Exception {
        Users user = userInOrganization("hashedreset", "OldPassword1");
        String emailed = passwordResetTokenService.createToken(user);
        String stored = passwordResetTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId())).findFirst().orElseThrow().getToken();
        assertThat(stored).isNotEqualTo(emailed).isEqualTo(TokenHash.sha256Hex(emailed));

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ResetPasswordRequestDTO(stored, "NewPassword2"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ResetPasswordRequestDTO(emailed, "NewPassword2"))))
                .andExpect(status().isOk());
    }

    /**
     * OWASP API2: a reset through the emailed link ends every session and lifts
     * the login lock (the forgotten password is often why the account locked).
     */
    @Test
    @Order(22)
    void resetPasswordEndsEverySessionAndLiftsTheLock() throws Exception {
        Users user = userInOrganization("resetsessions", "OldPassword1");
        String refresh = login("resetsessions", "OldPassword1").getCookie(RefreshCookieFactory.COOKIE_NAME).getValue();
        user = usersRepository.findById(user.getId()).orElseThrow();
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        usersRepository.save(user);
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(TokenHash.sha256Hex("reset-sessions")).user(user)
                .expiryDate(LocalDateTime.of(2030, Month.JANUARY, 1, 12, 0)).build());

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ResetPasswordRequestDTO("reset-sessions", "NewPassword2"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, refresh)))
                .andExpect(status().isUnauthorized());
        Users after = usersRepository.findById(user.getId()).orElseThrow();
        assertEquals(0, after.getFailedLoginAttempts());
        org.junit.jupiter.api.Assertions.assertNull(after.getLockedUntil());
        assertNotNull(after.getCredentialsChangedAt());
        login("resetsessions", "NewPassword2");
    }

    /**
     * Test that reusing a refresh token after it has been rotated is
     * detected and revokes every refresh token belonging to the user,
     * forcing re-authentication on all sessions.
     */
    @Test
    @Order(29)
    void shouldDetectRefreshTokenReuseAndRevokeAllSessions() throws Exception {
        verifiedUser("reuseuser", "pass123");

        MockHttpServletResponse loginResponse = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthRequestDTO("reuseuser", "pass123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String originalRefreshToken = loginResponse.getCookie(RefreshCookieFactory.COOKIE_NAME).getValue();

        MockHttpServletResponse refreshResponse = mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, originalRefreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String rotatedRefreshToken = refreshResponse.getCookie(RefreshCookieFactory.COOKIE_NAME).getValue();

        mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, originalRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Riutilizzo del refresh token rilevato")));

        mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, rotatedRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test refresh fails with an invalid token.
     */
    @Test
    @Order(23)
    void shouldReturnUnauthorizedWhenRefreshTokenInvalid() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, "nonexistent-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Refresh token non trovato")));
    }

    /**
     * Test refresh fails when the refresh_token cookie is absent entirely.
     */
    @Test
    @Order(28)
    void shouldReturnUnauthorizedWhenRefreshCookieMissing() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Refresh token non trovato")));
    }

    /**
     * Test refresh fails when the refresh_token cookie is present but blank
     * (non-null, isBlank() branch).
     */
    @Test
    @Order(30)
    void shouldReturnUnauthorizedWhenRefreshCookieBlank() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, "")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Refresh token non trovato")));
    }

    /**
     * Test logout is a no-op (still 204) when the refresh_token cookie is
     * present but blank (non-null, isBlank() branch).
     */
    @Test
    @Order(31)
    void shouldLogoutSuccessfullyWhenRefreshCookieBlank() throws Exception {
        mockMvc.perform(post("/auth/logout")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, "")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(RefreshCookieFactory.COOKIE_NAME, 0));
    }

    /**
     * Test logout revokes the refresh token (subsequent refresh should fail).
     */
    @Test
    @Order(24)
    void shouldLogoutAndRevokeRefreshToken() throws Exception {
        verifiedUser("logoutuser", "pass123");

        MockHttpServletResponse loginResponse = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthRequestDTO("logoutuser", "pass123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String refreshToken = loginResponse.getCookie(RefreshCookieFactory.COOKIE_NAME).getValue();

        mockMvc.perform(post("/auth/logout")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, refreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(RefreshCookieFactory.COOKIE_NAME, 0));

        mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie(RefreshCookieFactory.COOKIE_NAME, refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test logout is idempotent (no-op, still 204) when there is no refresh
     * cookie to revoke.
     */
    @Test
    @Order(25)
    void shouldLogoutSuccessfullyWhenNoRefreshCookiePresent() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(RefreshCookieFactory.COOKIE_NAME, 0));
    }

    /**
     * Test successful completion of invite.
     */
    @Test
    @Order(26)
    void shouldCompleteInviteSuccessfully() throws Exception {
        // Create an invite token in the database
        InviteToken inviteToken = new InviteToken();
        inviteToken.setToken(TokenHash.sha256Hex("valid-invite-token"));
        inviteToken.setUsername("inviteduser");
        inviteToken.setRole("MANAGER");
        inviteToken.setManagerId(managerId);
        inviteToken.setExpiryDate(LocalDateTime.of(2030, Month.JANUARY, 1, 12, 0));
        inviteToken.setUsed(false);
        inviteTokenRepository.save(inviteToken);

        // Create request object manually
        String requestJson = """
        {
            "token": "valid-invite-token",
            "password": "securePassword123"
        }
        """;

        mockMvc.perform(post("/auth/complete-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isNoContent());
    }

    /**
     * Full two-step login: password alone yields an MFA-pending token (no
     * access token yet), and exchanging it with the correct TOTP code at
     * /auth/2fa/verify completes the login.
     */
    @Test
    @Order(32)
    void shouldCompleteTwoStepLoginWithValidTotpCode() throws Exception {
        String secret = com.donatodev.bcm_backend.util.TotpUtil.generateSecret();
        // The session the second step issues is scoped to the user's organization.
        com.donatodev.bcm_backend.entity.Organization org = organizationRepository.save(
                com.donatodev.bcm_backend.entity.Organization.builder()
                        .name("Totp org").slug("totp-org-" + UUID.randomUUID()).build());
        usersRepository.save(Users.builder()
                .username("totpuser").passwordHash(passwordEncoder.encode("mypwd123")).verified(true)
                .organization(org)
                .totpEnabled(true).totpSecretEncrypted(totpEncryptionService.encrypt(secret))
                .manager(managersRepository.findById(managerId).orElseThrow())
                .role(rolesRepository.findById(roleId).orElseThrow()).build());

        AuthRequestDTO loginDto = new AuthRequestDTO("totpuser", "mypwd123");
        MockHttpServletResponse loginResponse = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").exists())
                .andReturn().getResponse();

        String mfaToken = objectMapper.readTree(loginResponse.getContentAsString()).get("mfaToken").asText();
        String code = totpCodeAt(secret, java.time.Instant.now().getEpochSecond() / 30);

        String verifyJson = objectMapper.writeValueAsString(
                new com.donatodev.bcm_backend.dto.MfaVerifyRequest(mfaToken, code));

        mockMvc.perform(post("/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(cookie().exists(RefreshCookieFactory.COOKIE_NAME));

        // The same code, while still valid, does not log in a second time.
        mockMvc.perform(post("/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(33)
    void shouldRejectTwoFactorVerifyWithWrongCode() throws Exception {
        String secret = com.donatodev.bcm_backend.util.TotpUtil.generateSecret();
        // With an organization, so the request reaches the code check itself.
        com.donatodev.bcm_backend.entity.Organization org = organizationRepository.save(
                com.donatodev.bcm_backend.entity.Organization.builder()
                        .name("Totp org 2").slug("totp-org-" + UUID.randomUUID()).build());
        Users saved = usersRepository.save(Users.builder()
                .username("totpuser2").passwordHash(passwordEncoder.encode("mypwd123")).verified(true)
                .organization(org)
                .totpEnabled(true).totpSecretEncrypted(totpEncryptionService.encrypt(secret))
                .manager(managersRepository.findById(managerId).orElseThrow())
                .role(rolesRepository.findById(roleId).orElseThrow()).build());

        AuthRequestDTO loginDto = new AuthRequestDTO("totpuser2", "mypwd123");
        MockHttpServletResponse loginResponse = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String mfaToken = objectMapper.readTree(loginResponse.getContentAsString()).get("mfaToken").asText();
        String verifyJson = objectMapper.writeValueAsString(
                new com.donatodev.bcm_backend.dto.MfaVerifyRequest(mfaToken, "000000"));

        mockMvc.perform(post("/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyJson))
                .andExpect(status().isUnauthorized());

        // The failed attempt is stored even though the request failed: the
        // transaction is not rolled back on a wrong code.
        assertEquals(1, usersRepository.findById(saved.getId()).orElseThrow().getFailedLoginAttempts());
    }

    private String totpCodeAt(String secret, long timeStep) throws Exception {
        java.lang.reflect.Method m = com.donatodev.bcm_backend.util.TotpUtil.class
                .getDeclaredMethod("generateCode", String.class, long.class);
        m.setAccessible(true);
        return (String) m.invoke(null, secret, timeStep);
    }
}
