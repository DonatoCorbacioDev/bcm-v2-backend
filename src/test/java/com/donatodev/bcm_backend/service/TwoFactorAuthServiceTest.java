package com.donatodev.bcm_backend.service;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.donatodev.bcm_backend.auth.AuthResponseDTO;
import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.TotpConfirmResponse;
import com.donatodev.bcm_backend.dto.TotpSetupResponse;
import com.donatodev.bcm_backend.entity.TotpRecoveryCode;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.TotpRecoveryCodeRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.TotpUtil;

@ExtendWith(MockitoExtension.class)
class TwoFactorAuthServiceTest {

    private static final String USERNAME = "admin";
    private static final Long ORG_ID = 5L;

    @Mock private UsersRepository usersRepository;
    @Mock private TotpRecoveryCodeRepository recoveryCodeRepository;
    @Mock private TotpEncryptionService totpEncryptionService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private RefreshTokenService refreshTokenService;

    private TwoFactorAuthService twoFactorAuthService;

    @BeforeEach
    void setup() {
        twoFactorAuthService = new TwoFactorAuthService(
                usersRepository, recoveryCodeRepository, totpEncryptionService,
                passwordEncoder, jwtUtils, refreshTokenService, new CurrentUserResolver(usersRepository));
        // pass-through "encryption" so plaintext secrets can be asserted on directly
        lenient().when(totpEncryptionService.encrypt(anyString())).thenAnswer(inv -> "ENC(" + inv.getArgument(0) + ")");
        lenient().when(totpEncryptionService.decrypt(anyString())).thenAnswer(inv -> {
            String value = inv.getArgument(0);
            return value.substring(4, value.length() - 1);
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private String codeAt(String secret, long timeStep) throws Exception {
        Method m = TotpUtil.class.getDeclaredMethod("generateCode", String.class, long.class);
        m.setAccessible(true);
        return (String) m.invoke(null, secret, timeStep);
    }

    private String currentCode(String secret) throws Exception {
        return codeAt(secret, Instant.now().getEpochSecond() / 30);
    }

    @Nested
    @DisplayName("setup")
    class Setup {

        @Test
        @DisplayName("generates and stores a new secret with 2FA not yet enabled")
        void shouldGenerateAndStoreSecret() {
            authenticateAs(USERNAME);
            Users user = Users.builder().username(USERNAME).build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            TotpSetupResponse response = twoFactorAuthService.setup();

            assertNotNull(response.secret());
            assertTrue(response.otpAuthUri().contains("BCM:admin"));
            assertFalse(user.isTotpEnabled());
            assertEquals("ENC(" + response.secret() + ")", user.getTotpSecretEncrypted());
            verify(usersRepository).save(user);
        }
    }

    @Nested
    @DisplayName("confirm")
    class Confirm {

        @Test
        @DisplayName("valid code enables 2FA and returns 10 recovery codes")
        void shouldConfirmWithValidCode() throws Exception {
            authenticateAs(USERNAME);
            String secret = TotpUtil.generateSecret();
            Users user = Users.builder().id(1L).username(USERNAME)
                    .totpSecretEncrypted("ENC(" + secret + ")").build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");

            TotpConfirmResponse response = twoFactorAuthService.confirm(currentCode(secret));

            assertEquals(10, response.recoveryCodes().size());
            assertEquals(10, response.recoveryCodes().stream().distinct().count());
            assertTrue(user.isTotpEnabled());
            verify(recoveryCodeRepository).deleteByUserId(1L);

            ArgumentCaptor<List<TotpRecoveryCode>> captor = ArgumentCaptor.forClass(List.class);
            verify(recoveryCodeRepository).saveAll(captor.capture());
            assertEquals(10, captor.getValue().size());
        }

        @Test
        @DisplayName("wrong code throws and leaves 2FA disabled")
        void shouldRejectWrongCode() {
            authenticateAs(USERNAME);
            String secret = TotpUtil.generateSecret();
            Users user = Users.builder().id(1L).username(USERNAME)
                    .totpSecretEncrypted("ENC(" + secret + ")").build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertThrows(IllegalArgumentException.class, () -> twoFactorAuthService.confirm("000000"));
            assertFalse(user.isTotpEnabled());
            verify(recoveryCodeRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("throws if setup was never called")
        void shouldRequireSetupFirst() {
            authenticateAs(USERNAME);
            Users user = Users.builder().id(1L).username(USERNAME).build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertThrows(IllegalArgumentException.class, () -> twoFactorAuthService.confirm("123456"));
        }
    }

    @Nested
    @DisplayName("disable")
    class Disable {

        @Test
        @DisplayName("valid TOTP code disables 2FA and clears the secret")
        void shouldDisableWithValidCode() throws Exception {
            authenticateAs(USERNAME);
            String secret = TotpUtil.generateSecret();
            Users user = Users.builder().id(1L).username(USERNAME).totpEnabled(true)
                    .totpSecretEncrypted("ENC(" + secret + ")").build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            twoFactorAuthService.disable(currentCode(secret));

            assertFalse(user.isTotpEnabled());
            assertNull(user.getTotpSecretEncrypted());
            verify(recoveryCodeRepository).deleteByUserId(1L);
        }

        @Test
        @DisplayName("a valid unused recovery code also disables 2FA")
        void shouldDisableWithRecoveryCode() {
            authenticateAs(USERNAME);
            String secret = TotpUtil.generateSecret();
            Users user = Users.builder().id(1L).username(USERNAME).totpEnabled(true)
                    .totpSecretEncrypted("ENC(" + secret + ")").build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            TotpRecoveryCode recoveryCode = TotpRecoveryCode.builder().id(9L).user(user).codeHash("hash").build();
            when(recoveryCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of(recoveryCode));
            when(passwordEncoder.matches("ABCD-1234", "hash")).thenReturn(true);

            twoFactorAuthService.disable("ABCD-1234");

            assertFalse(user.isTotpEnabled());
            assertNotNull(recoveryCode.getUsedAt());
        }

        @Test
        @DisplayName("throws when 2FA isn't enabled")
        void shouldThrowWhenNotEnabled() {
            authenticateAs(USERNAME);
            Users user = Users.builder().id(1L).username(USERNAME).totpEnabled(false).build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertThrows(IllegalArgumentException.class, () -> twoFactorAuthService.disable("123456"));
        }

        @Test
        @DisplayName("throws on an invalid code")
        void shouldThrowOnInvalidCode() {
            authenticateAs(USERNAME);
            String secret = TotpUtil.generateSecret();
            Users user = Users.builder().id(1L).username(USERNAME).totpEnabled(true)
                    .totpSecretEncrypted("ENC(" + secret + ")").build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(recoveryCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of());

            assertThrows(IllegalArgumentException.class, () -> twoFactorAuthService.disable("000000"));
            assertTrue(user.isTotpEnabled());
        }

        @Test
        @DisplayName("throws when 2FA is enabled but no secret was ever stored")
        void shouldThrowWhenEnabledWithoutSecret() {
            authenticateAs(USERNAME);
            Users user = Users.builder().id(1L).username(USERNAME).totpEnabled(true).build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertThrows(IllegalArgumentException.class, () -> twoFactorAuthService.disable("123456"));
        }

        @Test
        @DisplayName("throws when an unused recovery code exists but doesn't match")
        void shouldThrowWhenRecoveryCodeDoesNotMatch() {
            authenticateAs(USERNAME);
            String secret = TotpUtil.generateSecret();
            Users user = Users.builder().id(1L).username(USERNAME).totpEnabled(true)
                    .totpSecretEncrypted("ENC(" + secret + ")").build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            TotpRecoveryCode recoveryCode = TotpRecoveryCode.builder().id(9L).user(user).codeHash("hash").build();
            when(recoveryCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of(recoveryCode));
            when(passwordEncoder.matches("WRONG-CODE", "hash")).thenReturn(false);

            assertThrows(IllegalArgumentException.class, () -> twoFactorAuthService.disable("WRONG-CODE"));
            assertNull(recoveryCode.getUsedAt());
            assertTrue(user.isTotpEnabled());
        }
    }

    @Nested
    @DisplayName("status")
    class Status {

        @Test
        @DisplayName("reflects the user's current enabled flag")
        void shouldReturnEnabledFlag() {
            authenticateAs(USERNAME);
            Users user = Users.builder().username(USERNAME).totpEnabled(true).build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertTrue(twoFactorAuthService.status().enabled());
        }
    }

    @Nested
    @DisplayName("verifyLogin")
    class VerifyLogin {

        @Test
        @DisplayName("valid MFA token + valid TOTP code issues real tokens")
        void shouldVerifyAndIssueTokens() throws Exception {
            String secret = TotpUtil.generateSecret();
            Users user = Users.builder().username(USERNAME).totpEnabled(true)
                    .totpSecretEncrypted("ENC(" + secret + ")").build();

            when(jwtUtils.isMfaPendingToken("pending-token")).thenReturn(true);
            when(jwtUtils.getUsernameFromToken("pending-token")).thenReturn(USERNAME);
            when(jwtUtils.getOrganizationIdFromToken("pending-token")).thenReturn(ORG_ID);
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.of(user));
            when(jwtUtils.generateToken(user)).thenReturn("real-access-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn("real-refresh-token");

            AuthResponseDTO response = twoFactorAuthService.verifyLogin("pending-token", currentCode(secret));

            assertEquals("real-access-token", response.token());
            assertEquals("real-refresh-token", response.refreshToken());
        }

        @Test
        @DisplayName("a valid unused recovery code also completes the login and gets marked used")
        void shouldVerifyWithRecoveryCode() {
            String secret = TotpUtil.generateSecret();
            Users user = Users.builder().id(1L).username(USERNAME).totpEnabled(true)
                    .totpSecretEncrypted("ENC(" + secret + ")").build();

            when(jwtUtils.isMfaPendingToken("pending-token")).thenReturn(true);
            when(jwtUtils.getUsernameFromToken("pending-token")).thenReturn(USERNAME);
            when(jwtUtils.getOrganizationIdFromToken("pending-token")).thenReturn(ORG_ID);
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.of(user));

            TotpRecoveryCode recoveryCode = TotpRecoveryCode.builder().id(9L).user(user).codeHash("hash").build();
            when(recoveryCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of(recoveryCode));
            when(passwordEncoder.matches("ABCD-1234", "hash")).thenReturn(true);
            when(jwtUtils.generateToken(user)).thenReturn("real-access-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn("real-refresh-token");

            AuthResponseDTO response = twoFactorAuthService.verifyLogin("pending-token", "ABCD-1234");

            assertEquals("real-access-token", response.token());
            assertNotNull(recoveryCode.getUsedAt());
        }

        @Test
        @DisplayName("rejects a token that isn't MFA-pending")
        void shouldRejectNonPendingToken() {
            when(jwtUtils.isMfaPendingToken("not-pending")).thenReturn(false);

            assertThrows(BadCredentialsException.class,
                    () -> twoFactorAuthService.verifyLogin("not-pending", "123456"));
        }

        @Test
        @DisplayName("rejects when the resolved user no longer has 2FA enabled")
        void shouldRejectWhenTotpNoLongerEnabled() {
            Users user = Users.builder().username(USERNAME).totpEnabled(false).build();

            when(jwtUtils.isMfaPendingToken("pending-token")).thenReturn(true);
            when(jwtUtils.getUsernameFromToken("pending-token")).thenReturn(USERNAME);
            when(jwtUtils.getOrganizationIdFromToken("pending-token")).thenReturn(ORG_ID);
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.of(user));

            assertThrows(BadCredentialsException.class,
                    () -> twoFactorAuthService.verifyLogin("pending-token", "123456"));
        }

        @Test
        @DisplayName("rejects a wrong code")
        void shouldRejectWrongCode() {
            String secret = TotpUtil.generateSecret();
            Users user = Users.builder().id(1L).username(USERNAME).totpEnabled(true)
                    .totpSecretEncrypted("ENC(" + secret + ")").build();

            when(jwtUtils.isMfaPendingToken("pending-token")).thenReturn(true);
            when(jwtUtils.getUsernameFromToken("pending-token")).thenReturn(USERNAME);
            when(jwtUtils.getOrganizationIdFromToken("pending-token")).thenReturn(ORG_ID);
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.of(user));
            when(recoveryCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of());

            assertThrows(BadCredentialsException.class,
                    () -> twoFactorAuthService.verifyLogin("pending-token", "000000"));
        }

        @Test
        @DisplayName("rejects when the resolved user has 2FA enabled but no secret was ever stored")
        void shouldRejectWhenEnabledWithoutSecret() {
            Users user = Users.builder().username(USERNAME).totpEnabled(true).build();

            when(jwtUtils.isMfaPendingToken("pending-token")).thenReturn(true);
            when(jwtUtils.getUsernameFromToken("pending-token")).thenReturn(USERNAME);
            when(jwtUtils.getOrganizationIdFromToken("pending-token")).thenReturn(ORG_ID);
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.of(user));

            assertThrows(BadCredentialsException.class,
                    () -> twoFactorAuthService.verifyLogin("pending-token", "123456"));
        }

        @Test
        @DisplayName("rejects when no user matches the token's identity")
        void shouldRejectWhenUserNotFound() {
            when(jwtUtils.isMfaPendingToken("pending-token")).thenReturn(true);
            when(jwtUtils.getUsernameFromToken("pending-token")).thenReturn(USERNAME);
            when(jwtUtils.getOrganizationIdFromToken("pending-token")).thenReturn(ORG_ID);
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.empty());

            assertThrows(BadCredentialsException.class,
                    () -> twoFactorAuthService.verifyLogin("pending-token", "123456"));
        }
    }

    @Test
    @DisplayName("resolveCurrentUser throws when there is no authenticated user")
    void shouldThrowWhenNotAuthenticated() {
        assertThrows(UserNotFoundException.class, () -> twoFactorAuthService.status());
    }
}
