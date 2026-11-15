package com.donatodev.bcm_backend.auth;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

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

    @Mock
    private LoginAttemptMonitor loginAttemptMonitor;

    @Mock
    private com.donatodev.bcm_backend.security.PublicDemoGuard publicDemoGuard;

    private AuthService authService;

    // The real SessionIssuer and policy over the mocks: what a password leads
    // to is part of what this class decides. "demo_admin" is exempt, as in production.
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("dummy-hash");
        authService = new AuthService(usersRepository, passwordEncoder,
                new SessionIssuer(jwtUtils, refreshTokenService, new TwoFactorPolicy("demo_admin")),
                loginAttemptMonitor, publicDemoGuard);
    }

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

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateToken(user)).thenReturn("fake-jwt-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn("fake-refresh-token");

            LoginOutcome outcome = authService.authenticate("admin", "password");

            assertFalse(outcome.mfaRequired());
            assertEquals("fake-jwt-token", outcome.tokens().token());
            assertEquals("fake-refresh-token", outcome.tokens().refreshToken());
        }

        /**
         * Test authentication throws UsernameNotFoundException if username not found.
         */
        @Test
        @Order(2)
        @DisplayName("Authenticate throws on wrong username")
        void shouldThrowIfUsernameNotFound() {
            when(usersRepository.findAllByUsername("ghost")).thenReturn(List.of());

            BadCredentialsException ex =
                    assertThrows(BadCredentialsException.class, () -> authService.authenticate("ghost", "password"));
            assertEquals("Nome utente o password non validi", ex.getMessage());
            // As long as a real check, so the timing does not tell the name is unknown.
            org.mockito.Mockito.verify(passwordEncoder).matches("password", "dummy-hash");
            org.mockito.Mockito.verifyNoInteractions(loginAttemptMonitor);
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
            assertEquals("Nome utente o password non validi", ex.getMessage());
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
            assertTrue(ex.getMessage().contains("non verificato"));
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
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);

            AmbiguousUsernameException ex = assertThrows(AmbiguousUsernameException.class,
                    () -> authService.authenticate("admin", "password"));
            assertTrue(ex.getMessage().contains("più account"));
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

            when(usersRepository.findByUsernameAndOrganizationSlug("admin", "org-a")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateToken(user)).thenReturn("fake-jwt-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn("fake-refresh-token");

            LoginOutcome outcome = authService.authenticate("admin", "password", "org-a");

            assertEquals("fake-jwt-token", outcome.tokens().token());
            assertEquals("fake-refresh-token", outcome.tokens().refreshToken());
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

            BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                    () -> authService.authenticate("admin", "password", "unknown-org"));
            assertEquals("Nome utente o password non validi", ex.getMessage());
        }

        /**
         * Test authentication treats a blank (non-null) organizationSlug the same
         * as no organizationSlug, falling back to lookup by username alone.
         */
        @Test
        @Order(8)
        @DisplayName("Authenticate with blank organizationSlug falls back to username lookup")
        void shouldAuthenticateWithBlankOrganizationSlug() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .build();

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateToken(user)).thenReturn("fake-jwt-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn("fake-refresh-token");

            LoginOutcome outcome = authService.authenticate("admin", "password", "");

            assertEquals("fake-jwt-token", outcome.tokens().token());
            assertEquals("fake-refresh-token", outcome.tokens().refreshToken());
        }

        /**
         * Test authentication returns an MFA-pending outcome instead of tokens
         * when the account has 2FA enabled.
         */
        @Test
        @Order(9)
        @DisplayName("Authenticate returns MFA-pending outcome when 2FA is enabled")
        void shouldReturnMfaPendingWhenTotpEnabled() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .totpEnabled(true)
                    .build();

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateMfaPendingToken(user)).thenReturn("fake-mfa-pending-token");

            LoginOutcome outcome = authService.authenticate("admin", "password");

            assertTrue(outcome.mfaRequired());
            assertEquals("fake-mfa-pending-token", outcome.mfaToken());
        }

        @Test
        @DisplayName("An ADMIN without 2FA gets only a setup token, never a session")
        void adminWithoutTwoFactorMustSetItUp() {
            Users admin = Users.builder().username("boss").passwordHash("hashedpwd").verified(true)
                    .role(com.donatodev.bcm_backend.entity.Roles.builder().role("ADMIN").build()).build();
            when(usersRepository.findAllByUsername("boss")).thenReturn(List.of(admin));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateMfaSetupToken(admin)).thenReturn("setup-token");

            LoginOutcome outcome = authService.authenticate("boss", "password");

            assertTrue(outcome.mfaSetupRequired());
            assertEquals("setup-token", outcome.setupToken());
            assertNull(outcome.tokens());
            org.mockito.Mockito.verifyNoInteractions(refreshTokenService);
        }

        @Test
        @DisplayName("The exempt demo admin still signs in with a password")
        void exemptAdminSignsIn() {
            Users admin = Users.builder().username("demo_admin").passwordHash("hashedpwd").verified(true)
                    .role(com.donatodev.bcm_backend.entity.Roles.builder().role("ADMIN").build()).build();
            when(usersRepository.findAllByUsername("demo_admin")).thenReturn(List.of(admin));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateToken(admin)).thenReturn("access");
            when(refreshTokenService.createRefreshToken(admin)).thenReturn("refresh");

            LoginOutcome outcome = authService.authenticate("demo_admin", "password");

            assertEquals("access", outcome.tokens().token());
            assertFalse(outcome.mfaSetupRequired());
        }

        @Test
        @Order(10)
        @DisplayName("Wrong password increments the failed-attempt counter")
        void shouldIncrementFailedAttemptsOnWrongPassword() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .failedLoginAttempts(2)
                    .build();

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
            when(passwordEncoder.matches("wrongpass", "hashedpwd")).thenReturn(false);

            assertThrows(BadCredentialsException.class,
                    () -> authService.authenticate("admin", "wrongpass"));

            assertEquals(3, user.getFailedLoginAttempts());
            assertEquals(null, user.getLockedUntil());
            org.mockito.Mockito.verify(loginAttemptMonitor).passwordFailed(user);
            org.mockito.Mockito.verify(loginAttemptMonitor, org.mockito.Mockito.never()).locked(user);
        }

        @Test
        @Order(11)
        @DisplayName("5th consecutive wrong password locks the account")
        void shouldLockAccountAfterFifthFailedAttempt() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .failedLoginAttempts(4)
                    .build();

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
            when(passwordEncoder.matches("wrongpass", "hashedpwd")).thenReturn(false);

            assertThrows(BadCredentialsException.class,
                    () -> authService.authenticate("admin", "wrongpass"));

            assertEquals(5, user.getFailedLoginAttempts());
            assertTrue(user.getLockedUntil().isAfter(LocalDateTime.now()));
            org.mockito.Mockito.verify(loginAttemptMonitor).passwordFailed(user);
            org.mockito.Mockito.verify(loginAttemptMonitor).locked(user);
        }

        @Test
        @Order(12)
        @DisplayName("Login is refused while the account is locked, even with the right password")
        void shouldRejectLoginWhileLocked() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .failedLoginAttempts(5)
                    .lockedUntil(LocalDateTime.now().plusMinutes(10))
                    .build();

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);

            // Answered like a wrong password: saying "locked" would tell the account exists.
            BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                    () -> authService.authenticate("admin", "password"));
            assertEquals("Nome utente o password non validi", ex.getMessage());
            assertEquals(5, user.getFailedLoginAttempts());
            org.mockito.Mockito.verifyNoInteractions(loginAttemptMonitor, refreshTokenService);
        }

        @Test
        @Order(13)
        @DisplayName("Login succeeds once a stale lock has expired, and resets the counter")
        void shouldAllowLoginAfterLockExpires() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .failedLoginAttempts(5)
                    .lockedUntil(LocalDateTime.now().minusMinutes(1))
                    .build();

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateToken(user)).thenReturn("fake-jwt-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn("fake-refresh-token");

            LoginOutcome outcome = authService.authenticate("admin", "password");

            assertEquals("fake-jwt-token", outcome.tokens().token());
            assertEquals(0, user.getFailedLoginAttempts());
            assertEquals(null, user.getLockedUntil());
        }

        @Test
        @Order(14)
        @DisplayName("Successful login resets a nonzero failed-attempt counter")
        void shouldResetFailedAttemptsOnSuccessfulLogin() {
            Users user = Users.builder()
                    .username("admin")
                    .passwordHash("hashedpwd")
                    .verified(true)
                    .failedLoginAttempts(3)
                    .build();

            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(user));
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateToken(user)).thenReturn("fake-jwt-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn("fake-refresh-token");

            authService.authenticate("admin", "password");

            assertEquals(0, user.getFailedLoginAttempts());
        }

        @Test
        @DisplayName("An ambiguous name with a wrong password gets the generic answer, not the ambiguity")
        void ambiguousNameWithAWrongPassword() {
            Users userOrgA = Users.builder().username("admin").passwordHash("hashA").verified(true).build();
            Users userOrgB = Users.builder().username("admin").passwordHash("hashB").verified(true).build();
            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(userOrgA, userOrgB));

            BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                    () -> authService.authenticate("admin", "wrong"));

            assertEquals("Nome utente o password non validi", ex.getMessage());
            assertEquals(1, userOrgA.getFailedLoginAttempts());
            assertEquals(1, userOrgB.getFailedLoginAttempts());
        }

        @Test
        @DisplayName("An ambiguous name whose password is right for one account signs that one in")
        void ambiguousNameRightForOneAccount() {
            Users userOrgA = Users.builder().username("admin").passwordHash("hashA").verified(true).build();
            Users userOrgB = Users.builder().username("admin").passwordHash("hashB").verified(true).build();
            when(usersRepository.findAllByUsername("admin")).thenReturn(List.of(userOrgA, userOrgB));
            org.mockito.Mockito.lenient().when(passwordEncoder.matches(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
            when(passwordEncoder.matches("password", "hashB")).thenReturn(true);
            when(jwtUtils.generateToken(userOrgB)).thenReturn("access-b");
            when(refreshTokenService.createRefreshToken(userOrgB)).thenReturn("refresh-b");

            LoginOutcome outcome = authService.authenticate("admin", "password");

            assertEquals("access-b", outcome.tokens().token());
            assertEquals(0, userOrgA.getFailedLoginAttempts());
        }

        @Test
        @DisplayName("The public demo account, whose password is public, is never locked")
        void publicDemoIsNeverLocked() {
            Users demo = Users.builder().username("demo").passwordHash("hashedpwd").verified(true)
                    .failedLoginAttempts(4).lockedUntil(LocalDateTime.now().plusMinutes(10)).build();
            when(usersRepository.findAllByUsername("demo")).thenReturn(List.of(demo));
            org.mockito.Mockito.lenient().when(passwordEncoder.matches(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
            when(publicDemoGuard.isPublicDemo("demo")).thenReturn(true);
            when(passwordEncoder.matches("password", "hashedpwd")).thenReturn(true);
            when(jwtUtils.generateToken(demo)).thenReturn("access");
            when(refreshTokenService.createRefreshToken(demo)).thenReturn("refresh");

            assertThrows(BadCredentialsException.class, () -> authService.authenticate("demo", "wrong"));
            assertEquals(4, demo.getFailedLoginAttempts());
            org.mockito.Mockito.verify(loginAttemptMonitor).passwordFailed(demo);
            org.mockito.Mockito.verify(loginAttemptMonitor, org.mockito.Mockito.never()).locked(any());

            // A lock left from before the rule does not keep the visitors out either.
            assertEquals("access", authService.authenticate("demo", "password").tokens().token());
        }
    }
}