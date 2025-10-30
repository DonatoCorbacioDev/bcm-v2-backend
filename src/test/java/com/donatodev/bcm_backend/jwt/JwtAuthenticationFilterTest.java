package com.donatodev.bcm_backend.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.CustomUserDetailsService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwtAuthenticationFilterTest {
	
	private static final String DUMMY_PASSWORD_ENCODED = "encoded";
	private static final String DUMMY_PASSWORD_PLAIN = "pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils; // Mocked

    @Autowired
    private CustomUserDetailsService userDetailsService; // Mocked

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private ManagersRepository managersRepository;

    private String token;

    /**
     * Initializes mock behavior and test data before each test case.
     */
    @SuppressWarnings("unused") 
    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();

        Roles role = rolesRepository.save(Roles.builder().role("MANAGER").build());
        Managers manager = managersRepository.save(Managers.builder()
                .firstName("Mario").lastName("Rossi").email("mario@fake.com")
                .department("IT").phoneNumber("123456").build());

        Users user = usersRepository.save(Users.builder()
                .username("jwtuser")
                .passwordHash("encoded")
                .verified(true)
                .role(role)
                .manager(manager)
                .build());

        token = "validToken";
        Mockito.reset(jwtUtils, userDetailsService);
        Mockito.when(jwtUtils.generateToken(user)).thenReturn(token);
        Mockito.when(userDetailsService.loadUserByUsername(user.getUsername()))
        .thenReturn(org.springframework.security.core.userdetails.User
            .withUsername(user.getUsername()).password(DUMMY_PASSWORD_ENCODED).roles("MANAGER").build());
        Mockito.when(jwtUtils.validateToken(Mockito.eq(token), Mockito.any())).thenReturn(true);
        Mockito.when(jwtUtils.getUsernameFromToken(token)).thenReturn("jwtuser");
    }

    /**
     * Injects mocked beans into the Spring context for testing.
     */
    @SuppressWarnings("unused") 
    @TestConfiguration
    static class MockConfig {
        @Bean
        public JwtUtils jwtUtils() {
            return Mockito.mock(JwtUtils.class);
        }

        @Bean
        public CustomUserDetailsService customUserDetailsService() {
            return Mockito.mock(CustomUserDetailsService.class);
        }
    }

    @Test
    @DisplayName("Should skip filter if Authorization header is missing")
    void shouldSkipIfNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should skip filter if Authorization header does not start with Bearer")
    void shouldSkipIfHeaderNotBearer() throws Exception {
        mockMvc.perform(get("/auth/me")
            .header("Authorization", "Basic abcdef"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should skip filter if username is null (invalid token)")
    void shouldSkipIfUsernameIsNull() throws Exception {
        Mockito.when(jwtUtils.getUsernameFromToken("invalidtoken")).thenReturn(null);
        mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer invalidtoken"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should skip filter if authentication already exists in SecurityContext")
    @WithMockUser(username = "jwtuser", roles = "MANAGER")
    void shouldSkipIfAuthenticationAlreadyExists() throws Exception {
        var userDetails = org.springframework.security.core.userdetails.User
            .withUsername("jwtuser").password(DUMMY_PASSWORD_PLAIN).roles("MANAGER").build();

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userDetails, "pass", userDetails.getAuthorities())
        );

        mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should skip filter if token is invalid")
    void shouldSkipIfTokenIsInvalid() throws Exception {
        String invalidToken = "completelyInvalidToken";
        Mockito.when(jwtUtils.getUsernameFromToken(invalidToken)).thenReturn(null);

        mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer " + invalidToken))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jwtuser"))));
    }

    @Test
    @DisplayName("Should authenticate user if token is valid and authentication is not present")
    void shouldAuthenticateIfTokenIsValid() throws Exception {
        mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("jwtuser")));
    }

    @Test
    @DisplayName("Should not authenticate if validateToken returns false")
    void shouldNotAuthenticateIfValidateTokenFalse() throws Exception {
        Users user = usersRepository.findAll().get(0);
        String testToken = "validToken";

        Mockito.when(jwtUtils.getUsernameFromToken(testToken)).thenReturn(user.getUsername());
        Mockito.when(userDetailsService.loadUserByUsername(user.getUsername()))
               .thenReturn(org.springframework.security.core.userdetails.User
                       .withUsername(user.getUsername()).password(DUMMY_PASSWORD_PLAIN).roles("MANAGER").build());
        Mockito.when(jwtUtils.validateToken(Mockito.eq(testToken), Mockito.any())).thenReturn(false);

        mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer " + testToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should skip authentication if userDetailsService throws exception")
    void shouldSkipIfUserDetailsServiceThrows() throws Exception {
        Users user = usersRepository.findAll().get(0);
        String testToken = "validToken";

        Mockito.when(jwtUtils.getUsernameFromToken(testToken)).thenReturn(user.getUsername());
        Mockito.when(userDetailsService.loadUserByUsername(user.getUsername()))
               .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer " + testToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should skip filter if getUsernameFromToken throws exception")
    void shouldSkipIfGetUsernameFromTokenThrows() throws Exception {
        String testToken = "exceptionToken";
        Mockito.when(jwtUtils.getUsernameFromToken(testToken)).thenThrow(new RuntimeException("JWT parse error"));

        mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer " + testToken))
            .andExpect(status().isUnauthorized());
    }
}
