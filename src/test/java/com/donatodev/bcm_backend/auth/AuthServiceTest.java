package com.donatodev.bcm_backend.auth;

import java.util.List;
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

import com.donatodev.bcm_backend.entity.RefreshToken;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.AmbiguousUsernameException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.RefreshTokenService;

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

    @Mock
    private RefreshTokenService refreshTokenService;

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
        @DisplayName("Authenticate success returns both tokens")
        void shouldAuthenticateAndReturnToken() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .build();

            RefreshToken fakeRefreshToken = RefreshToken.builder().token("fake-refresh-token").build();

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateToken(user)).thenReturn("fake-jwt-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn(fakeRefreshToken);

            AuthResponseDTO response = authService.authenticate("admin", "password");

            assertEquals("fake-jwt-token", response.token());
            assertEquals("fake-refresh-token", response.refreshToken());
        }

        /**
         * Test authentication throws UsernameNotFoundException if username not found.
         */
        @Test
        @Order(2)
        @DisplayName("Authenticate throws on wrong username")
        void shouldThrowIfUsernameNotFound() {
            when(usersRepository.findAllByUsername("ghost")).thenReturn(List.of());

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

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
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

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.authenticate("admin", "password"));
            assertTrue(ex.getMessage().contains("not verified"));
        }

        /**
         * Test authentication throws AmbiguousUsernameException if the username
         * matches users in more than one organization and no organizationSlug is given.
         */
        @Test
        @Order(5)
        @DisplayName("Authenticate throws if username is ambiguous across organizations")
        void shouldThrowIfUsernameAmbiguous() {
            Users userOrgA = Users.builder().username("admin").passwordHash("hashedpwd").verified(true).build();
            Users userOrgB = Users.builder().username("admin").passwordHash("hashedpwd").verified(true).build();

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(userOrgA, userOrgB));

            AmbiguousUsernameException ex = assertThrows(AmbiguousUsernameException.class,
                    () -> authService.authenticate("admin", "password"));
            assertTrue(ex.getMessage().contains("Multiple accounts"));
        }

        /**
         * Test authentication uses organizationSlug to disambiguate when provided.
         */
        @Test
        @Order(6)
        @DisplayName("Authenticate with organizationSlug resolves the correct account")
        void shouldAuthenticateUsingOrganizationSlug() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .build();

            RefreshToken fakeRefreshToken = RefreshToken.builder().token("fake-refresh-token").build();

            when(usersRepository.findByUsernameAndOrganizationSlug("admin", "org-a")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateToken(user)).thenReturn("fake-jwt-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn(fakeRefreshToken);

            AuthResponseDTO response = authService.authenticate("admin", "password", "org-a");

            assertEquals("fake-jwt-token", response.token());
            assertEquals("fake-refresh-token", response.refreshToken());
        }

        /**
         * Test authentication throws UsernameNotFoundException if organizationSlug
         * does not match any account for the given username.
         */
        @Test
        @Order(7)
        @DisplayName("Authenticate throws if organizationSlug does not match any account")
        void shouldThrowIfOrganizationSlugDoesNotMatch() {
            when(usersRepository.findByUsernameAndOrganizationSlug("admin", "unknown-org")).thenReturn(Optional.empty());

            UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                    () -> authService.authenticate("admin", "password", "unknown-org"));
            assertEquals("Invalid username or password", ex.getMessage());
        }
    }
}