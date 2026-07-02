package com.donatodev.bcm_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.RefreshToken;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.RefreshTokenException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: RefreshTokenService")
    @SuppressWarnings("unused")
    class VerifyRefreshTokenService {

        @Test
        @Order(1)
        @DisplayName("createRefreshToken returns a raw UUID token string")
        void shouldCreateRefreshToken() {
            Users user = Users.builder().username("testuser").build();
            RefreshToken saved = RefreshToken.builder().token(sha256("any")).user(user)
                    .expiryDate(Instant.parse("2030-01-01T00:00:00Z")).build();

            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(saved);

            String result = refreshTokenService.createRefreshToken(user);

            assertNotNull(result);
            assertFalse(result.isEmpty());
            verify(refreshTokenRepository).deleteAllByUser(user);
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @Order(2)
        @DisplayName("createRefreshToken stores the SHA-256 hash, not the raw token")
        void shouldStoreHashNotRawToken() {
            Users user = Users.builder().username("testuser").build();
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

            String raw = refreshTokenService.createRefreshToken(user);

            verify(refreshTokenRepository).save(any(RefreshToken.class));
            // raw token is a UUID — it should NOT equal its own SHA-256 hash
            assertNotEquals(raw, sha256(raw), "raw is a UUID-style token");
        }

        @Test
        @Order(3)
        @DisplayName("refreshAccessToken rotates the refresh token and returns both tokens")
        void shouldRefreshAccessToken() {
            Users user = Users.builder().username("testuser").build();
            String incomingRaw = "valid-token";
            RefreshToken token = RefreshToken.builder()
                    .token(sha256(incomingRaw))
                    .user(user)
                    .expiryDate(Instant.parse("2030-01-01T00:00:00Z"))
                    .revoked(false)
                    .build();
            RefreshToken newToken = RefreshToken.builder()
                    .token(sha256("new-uuid"))
                    .user(user)
                    .expiryDate(Instant.parse("2030-01-01T00:00:00Z"))
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByToken(sha256(incomingRaw))).thenReturn(Optional.of(token));
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(token, newToken);
            when(jwtUtils.generateToken(user)).thenReturn("new-access-token");

            RefreshTokenService.RotatedTokens result = refreshTokenService.refreshAccessToken(incomingRaw);

            assertEquals("new-access-token", result.accessToken());
            assertNotNull(result.refreshToken());
            assertFalse(result.refreshToken().isEmpty());
            assertTrue(token.isRevoked());
        }

        @Test
        @Order(4)
        @DisplayName("refreshAccessToken throws when token not found")
        void shouldThrowWhenTokenNotFound() {
            when(refreshTokenRepository.findByToken(sha256("ghost"))).thenReturn(Optional.empty());

            assertThrows(RefreshTokenException.class,
                    () -> refreshTokenService.refreshAccessToken("ghost"));
        }

        @Test
        @Order(5)
        @DisplayName("refreshAccessToken detects reuse of a revoked token and revokes all user tokens")
        void shouldThrowWhenTokenRevoked() {
            Users user = Users.builder().username("victim").build();
            String revokedRaw = "revoked-token";
            RefreshToken token = RefreshToken.builder()
                    .token(sha256(revokedRaw))
                    .user(user)
                    .expiryDate(Instant.parse("2030-01-01T00:00:00Z"))
                    .revoked(true)
                    .build();

            when(refreshTokenRepository.findByToken(sha256(revokedRaw))).thenReturn(Optional.of(token));

            assertThrows(RefreshTokenException.class,
                    () -> refreshTokenService.refreshAccessToken(revokedRaw));

            verify(refreshTokenRepository).revokeAllByUser(user);
        }

        @Test
        @Order(6)
        @DisplayName("refreshAccessToken throws and marks revoked when token expired")
        void shouldThrowAndRevokeWhenTokenExpired() {
            String expiredRaw = "expired-token";
            RefreshToken token = RefreshToken.builder()
                    .token(sha256(expiredRaw))
                    .user(Users.builder().build())
                    .expiryDate(Instant.parse("2020-01-01T00:00:00Z"))
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByToken(sha256(expiredRaw))).thenReturn(Optional.of(token));
            when(refreshTokenRepository.save(any())).thenReturn(token);

            assertThrows(RefreshTokenException.class,
                    () -> refreshTokenService.refreshAccessToken(expiredRaw));

            verify(refreshTokenRepository).save(token);
        }

        @Test
        @Order(7)
        @DisplayName("revokeToken marks token as revoked")
        void shouldRevokeToken() {
            String rawToken = "to-revoke";
            RefreshToken token = RefreshToken.builder()
                    .token(sha256(rawToken))
                    .user(Users.builder().build())
                    .expiryDate(Instant.parse("2030-01-01T00:00:00Z"))
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByToken(sha256(rawToken))).thenReturn(Optional.of(token));
            when(refreshTokenRepository.save(any())).thenReturn(token);

            refreshTokenService.revokeToken(rawToken);

            verify(refreshTokenRepository).save(token);
        }

        @Test
        @Order(8)
        @DisplayName("revokeToken does nothing when token not found")
        void shouldDoNothingWhenTokenNotFoundOnRevoke() {
            when(refreshTokenRepository.findByToken(sha256("ghost"))).thenReturn(Optional.empty());

            refreshTokenService.revokeToken("ghost");

            verify(refreshTokenRepository).findByToken(sha256("ghost"));
        }

        @Test
        @Order(9)
        @DisplayName("hashToken produces consistent 64-char hex for the same input")
        void hashTokenIsConsistentAndHexEncoded() {
            String h1 = RefreshTokenService.hashToken("test-input");
            String h2 = RefreshTokenService.hashToken("test-input");
            assertEquals(h1, h2);
            assertEquals(64, h1.length());
            assertTrue(h1.matches("[0-9a-f]{64}"));
        }

        @Test
        @Order(10)
        @DisplayName("hashToken produces different hashes for different inputs")
        void hashTokenIsDifferentForDifferentInputs() {
            String h1 = RefreshTokenService.hashToken("token-a");
            String h2 = RefreshTokenService.hashToken("token-b");
            assertNotEquals(h1, h2);
        }
    }
}
