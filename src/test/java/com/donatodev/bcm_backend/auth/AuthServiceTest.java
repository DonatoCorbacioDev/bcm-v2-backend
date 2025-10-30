package com.donatodev.bcm_backend.auth;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Unit tests for {@link AuthService}.
 * <p>
 * Uses Mockito to mock dependencies and verify authentication logic,
 * including token generation, user verification, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class AuthServiceTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    /**
     * Nested class grouping all unit tests for authentication service.
     */
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: AuthService")
    @SuppressWarnings("unused")
    class VerifyAuthService {

        /**
         * Test successful authentication returns JWT token.
         */
        @Test
        @Order(1)
        @DisplayName("Authenticate success returns token")
        void shouldAuthenticateAndReturnToken() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .build();

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateToken(user)).thenReturn("fake-jwt-token");

            String token = authService.authenticate("admin", "password");

            assertEquals("fake-jwt-token", token);
        }

        /**
         * Test authentication throws UsernameNotFoundException if username not found.
         */
        @Test
        @Order(2)
        @DisplayName("Authenticate throws on wrong username")
        void shouldThrowIfUsernameNotFound() {
            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UsernameNotFoundException ex =
                    assertThrows(UsernameNotFoundException.class, () -> authService.authenticate("ghost", "password"));
            assertEquals("Invalid username or password", ex.getMessage());
        }

        /**
         * Test authentication throws BadCredentialsException if password is incorrect.
         */
        @Test
        @Order(3)
        @DisplayName("Authenticate throws on wrong password")
        void shouldThrowIfWrongPassword() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .build();

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongpass", "hashedpwd")).thenReturn(false);

            BadCredentialsException ex =
                    assertThrows(BadCredentialsException.class, () -> authService.authenticate("admin", "wrongpass"));
            assertEquals("Invalid username or password", ex.getMessage());
        }

        /**
         * Test authentication throws RuntimeException if user is not verified.
         */
        @Test
        @Order(4)
        @DisplayName("Authenticate throws if user not verified")
        void shouldThrowIfUserNotVerified() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(false)
                    .build();

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.authenticate("admin", "password"));
            assertTrue(ex.getMessage().contains("not verified"));
        }
    }
}