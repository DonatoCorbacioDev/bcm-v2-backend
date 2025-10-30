package com.donatodev.bcm_backend.auth;

import java.time.LocalDateTime;
import java.util.Collections;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.ForgotPasswordRequestDTO;
import com.donatodev.bcm_backend.dto.ResetPasswordRequestDTO;
import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.PasswordResetToken;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.entity.VerificationToken;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.PasswordResetTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.repository.VerificationTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for AuthController REST API endpoints.
 * <p>
 * This class tests user registration, email verification, login,
 * password reset processes, profile retrieval, error handling,
 * validation, and authorization checks.
 * Uses MockMvc to simulate HTTP requests in a Spring Boot test context.
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

    private Long roleId;
    private Long managerId;

    /**
     * Setup method executed before each test.
     * Cleans all relevant repositories and inserts a MANAGER role and a test manager.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        verificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
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
        UserDTO dto = new UserDTO(null, "donato", "abc123", managerId, roleId);

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
                .expiryDate(LocalDateTime.now().plusHours(1)).build());

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
        UserDTO dto = new UserDTO(null, "loginuser", "mypwd123", managerId, roleId);
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
                .andExpect(jsonPath("$.token").exists());
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
                .andExpect(content().string(containsString("Password reset link sent")));
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
                .expiryDate(LocalDateTime.now().plusHours(1)).build());

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
                .expiryDate(LocalDateTime.now().minusMinutes(1)).build());

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
                .expiryDate(LocalDateTime.now().minusMinutes(1)).build());

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
        UserDTO dto = new UserDTO(null, "broken", "pass123", 999L, roleId);

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
        UserDTO invalidDto = new UserDTO(null, "validUser", "123", managerId, roleId);

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

        UserDTO dto = new UserDTO(null, "testuser", "password", ghostManagerId, roleId);

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
     * Test unauthorized response when authentication exists but is not authenticated.
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
     * Test unauthorized response when principal is authenticated but is "anonymousUser".
     */
    @Test
    @Order(17)
    void shouldReturnUnauthorizedWhenAuthenticatedButPrincipalIsAnonymousUser() throws Exception {
        org.springframework.security.core.Authentication auth =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
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
    
}
