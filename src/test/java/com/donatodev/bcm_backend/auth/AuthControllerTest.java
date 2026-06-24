package com.donatodev.bcm_backend.auth;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
import com.donatodev.bcm_backend.entity.VerificationToken;
import com.donatodev.bcm_backend.repository.InviteTokenRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.PasswordResetTokenRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.repository.VerificationTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private Long roleId;
    private Long managerId;

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

    /**
     * Test successful user registration.
     */
    @Test
    @Order(1)
    void shouldRegisterSuccessfully() throws Exception {
        UserDTO dto = new UserDTO(null, "donato", "abc123", managerId, roleId, false, null);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("Registered user")));
    }

    /**
     * Test successful email verification with valid token.
     */
    @Test
    @Order(2)
    void shouldVerifyEmailSuccessfully() throws Exception {
        Users user = usersRepository.save(Users.builder()
                .username("verifyme").passwordHash("abc").verified(false)
                .manager(managersRepository.findById(managerId).orElseThrow())
                .role(rolesRepository.findById(roleId).orElseThrow()).build());

        verificationTokenRepository.save(VerificationToken.builder()
                .token("verify-token").user(user)
                .expiryDate(LocalDateTime.of(2030, Month.JANUARY, 1, 12, 0)).build());

        mockMvc.perform(get("/auth/verify?token=verify-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email verified successfully")));
    }

    /**
     * Test successful login after registration and verification.
     */
    @Test
    @Order(3)
    void shouldLoginSuccessfully() throws Exception {
        // Register new user
        UserDTO dto = new UserDTO(null, "loginuser", "mypwd123", managerId, roleId, false, null);
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Verify user manually
        Users user = usersRepository.findByUsername("loginuser").orElseThrow();
        user.setVerified(true);
        usersRepository.save(user);

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
                .andExpect(content().string(containsString("password reset link has been sent")));
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
                .andExpect(content().string(containsString("password reset link has been sent")));
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
                .token("reset-token").user(user)
                .expiryDate(LocalDateTime.of(2030, Month.JANUARY, 1, 12, 0)).build());

        ResetPasswordRequestDTO req = new ResetPasswordRequestDTO("reset-token", "newpwd");

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Password successfully reset")));
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
     * Test rejection of expired verification token.
     */
    @Test
    @Order(7)
    void shouldRejectExpiredVerificationToken() throws Exception {
        Users user = usersRepository.save(Users.builder()
                .username("expiredtoken").passwordHash("abc").verified(false)
                .manager(managersRepository.findById(managerId).orElseThrow())
                .role(rolesRepository.findById(roleId).orElseThrow()).build());

        verificationTokenRepository.save(VerificationToken.builder()
                .token("expired-verify-token").user(user)
                .expiryDate(LocalDateTime.of(2020, Month.JANUARY, 1, 12, 0)).build());

        mockMvc.perform(get("/auth/verify?token=expired-verify-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Verification token expired")));
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
                .token("expired-reset-token").user(user)
                .expiryDate(LocalDateTime.of(2020, Month.JANUARY, 1, 12, 0)).build());

        ResetPasswordRequestDTO req = new ResetPasswordRequestDTO("expired-reset-token", "newpwd");

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Token expired")));
    }

    /**
     * Test failure when registering with invalid manager ID.
     */
    @Test
    @Order(9)
    void shouldFailRegisterWithInvalidManagerId() throws Exception {
        // uso un managerId inesistente per forzare un'eccezione
        UserDTO dto = new UserDTO(null, "broken", "pass123", 999L, roleId, false, null);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(containsString("Manager ID 999 not found")));
    }

    /**
     * Test failure when resetting password with invalid token.
     */
    @Test
    @Order(10)
    void shouldFailResetPasswordWithInvalidToken() throws Exception {
        ResetPasswordRequestDTO req = new ResetPasswordRequestDTO("non-existent-token", "newpwd");

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid token")));
    }

    /**
     * Test bad request when verification token does not exist.
     */
    @Test
    @Order(11)
    void shouldReturnBadRequestWhenVerificationTokenIsInvalid() throws Exception {
        // Non esiste alcun token "invalid-token" nel DB
        mockMvc.perform(get("/auth/verify?token=invalid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid or expired verification token.")));
    }

    /**
     * Test validation error when password is too short.
     */
    @Test
    @Order(12)
    void shouldReturnValidationError() throws Exception {
        UserDTO invalidDto = new UserDTO(null, "validUser", "123", managerId, roleId, false, null);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").value(containsString("Password must be at least 6 characters")));
    }

    /**
     * Test handling of generic exceptions during registration.
     */
    @Test
    @Order(13)
    void shouldHandleGenericException() throws Exception {
        // Creo e poi elimino il manager per simulare errore
        Managers ghostManager = managersRepository.save(Managers.builder()
                .firstName("Ghost").lastName("Manager").email("ghost@test.com")
                .department("Ghost").phoneNumber("0000000000").build());

        Long ghostManagerId = ghostManager.getId();
        managersRepository.deleteById(ghostManagerId);

        UserDTO dto = new UserDTO(null, "testuser", "password", ghostManagerId, roleId, false, null);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(containsString("Internal error")));
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
                .andExpect(jsonPath("$.message").value(containsString("Unauthorized")));
    }

    /**
     * Test successful token refresh returns a new access token.
     */
    @Test
    @Order(22)
    void shouldRefreshTokenSuccessfully() throws Exception {
        UserDTO dto = new UserDTO(null, "refreshuser", "pass123", managerId, roleId, false, null);
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        Users user = usersRepository.findByUsername("refreshuser").orElseThrow();
        user.setVerified(true);
        usersRepository.save(user);

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

    /**
     * Test that reusing a refresh token after it has been rotated is
     * detected and revokes every refresh token belonging to the user,
     * forcing re-authentication on all sessions.
     */
    @Test
    @Order(29)
    void shouldDetectRefreshTokenReuseAndRevokeAllSessions() throws Exception {
        UserDTO dto = new UserDTO(null, "reuseuser", "pass123", managerId, roleId, false, null);
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        Users user = usersRepository.findByUsername("reuseuser").orElseThrow();
        user.setVerified(true);
        usersRepository.save(user);

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
                .andExpect(jsonPath("$.message").value(containsString("reuse detected")));

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
                .andExpect(jsonPath("$.message").value(containsString("Refresh token not found")));
    }

    /**
     * Test refresh fails when the refresh_token cookie is absent entirely.
     */
    @Test
    @Order(28)
    void shouldReturnUnauthorizedWhenRefreshCookieMissing() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Refresh token not found")));
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
                .andExpect(jsonPath("$.message").value(containsString("Refresh token not found")));
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
        UserDTO dto = new UserDTO(null, "logoutuser", "pass123", managerId, roleId, false, null);
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        Users user = usersRepository.findByUsername("logoutuser").orElseThrow();
        user.setVerified(true);
        usersRepository.save(user);

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
        inviteToken.setToken("valid-invite-token");
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
}
