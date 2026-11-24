package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.auth.TwoFactorPolicy;
import com.donatodev.bcm_backend.entity.RefreshToken;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.RefreshTokenException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.security.PublicDemoGuard;
import com.donatodev.bcm_backend.security.SecurityEvents;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtils jwtUtils;
    @Mock private TwoFactorPolicy twoFactorPolicy;
    @Mock private SessionRevocationService sessionRevocationService;
    @Mock private PublicDemoGuard publicDemoGuard;
    @Mock private SecurityEvents securityEvents;
    @Mock private com.donatodev.bcm_backend.auth.NewDeviceMonitor newDeviceMonitor;

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

    private RefreshToken active(String raw, Users user, Instant expiry) {
        RefreshToken token = RefreshToken.builder().id(UUID.randomUUID()).token(sha256(raw))
                .user(user).expiryDate(expiry).revoked(false).build();
        when(refreshTokenRepository.findByToken(sha256(raw))).thenReturn(Optional.of(token));
        return token;
    }

    @Nested
    @DisplayName("Unit Test: RefreshTokenService login and rotation")
    class Rotation {

        @Test
        @DisplayName("a login ends the account's live sessions and returns a 256-bit random token")
        void shouldCreateRefreshToken() {
            Users user = Users.builder().username("testuser").build();

            String first = refreshTokenService.createRefreshToken(user);
            String second = refreshTokenService.createRefreshToken(user);

            assertTrue(first.matches("[A-Za-z0-9_-]{43}"), "32 random bytes in base64url: " + first);
            assertNotEquals(first, second);
            verify(refreshTokenRepository, times(2)).deleteActiveByUser(user);
        }

        @Test
        @DisplayName("only the SHA-256 hash is stored, expiring after the session's length")
        void shouldStoreHashNotRawToken() {
            Users user = Users.builder().username("testuser").build();
            ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationMs", 60_000L);
            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);

            String raw = refreshTokenService.createRefreshToken(user);

            verify(refreshTokenRepository).save(saved.capture());
            assertEquals(sha256(raw), saved.getValue().getToken());
            assertTrue(saved.getValue().getExpiryDate().isBefore(Instant.now().plusSeconds(61)));
        }

        @Test
        @DisplayName("a login on the shared demo account keeps the other visitors' sessions")
        void demoLoginKeepsOtherVisitors() {
            when(publicDemoGuard.isPublicDemo("demo")).thenReturn(true);

            refreshTokenService.createRefreshToken(Users.builder().username("demo").build());

            verify(refreshTokenRepository, never()).deleteActiveByUser(any());
        }

        @Test
        @DisplayName("a refresh rotates the token, and the new one ends when the old one did")
        void shouldRefreshAccessToken() {
            Users user = Users.builder().username("testuser").build();
            Instant sessionEnd = Instant.now().plusSeconds(3600);
            RefreshToken token = active("valid-token", user, sessionEnd);
            when(refreshTokenRepository.revokeIfActive(token.getId())).thenReturn(1);
            when(jwtUtils.generateToken(user)).thenReturn("new-access-token");
            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);

            RefreshTokenService.RotatedTokens result = refreshTokenService.refreshAccessToken("valid-token");

            assertEquals("new-access-token", result.accessToken());
            verify(refreshTokenRepository).save(saved.capture());
            assertEquals(sha256(result.refreshToken()), saved.getValue().getToken());
            assertEquals(sessionEnd, saved.getValue().getExpiryDate());
        }

        @Test
        @DisplayName("a refresh ends the session of an account that must set 2FA up")
        void shouldNotRenewASessionThatNeedsTwoFactor() {
            Users admin = Users.builder().username("boss").build();
            RefreshToken token = active("valid-token", admin, Instant.now().plusSeconds(3600));
            when(refreshTokenRepository.revokeIfActive(token.getId())).thenReturn(1);
            when(twoFactorPolicy.blocksSession(admin)).thenReturn(true);

            assertThrows(RefreshTokenException.class, () -> refreshTokenService.refreshAccessToken("valid-token"));

            verify(refreshTokenRepository).revokeIfActive(token.getId());
            verify(jwtUtils, never()).generateToken(any());
        }

        @Test
        @DisplayName("an unknown token gets the one generic answer")
        void shouldThrowWhenTokenNotFound() {
            when(refreshTokenRepository.findByToken(sha256("ghost"))).thenReturn(Optional.empty());

            RefreshTokenException ex = assertThrows(RefreshTokenException.class,
                    () -> refreshTokenService.refreshAccessToken("ghost"));

            assertEquals(RefreshTokenService.INVALID_SESSION, ex.getMessage());
        }

        @Test
        @DisplayName("an expired token is deleted, not treated as a replay")
        void shouldDeleteWhenTokenExpired() {
            RefreshToken token = active("expired-token", Users.builder().username("late").build(),
                    Instant.parse("2020-01-01T00:00:00Z"));

            RefreshTokenException ex = assertThrows(RefreshTokenException.class,
                    () -> refreshTokenService.refreshAccessToken("expired-token"));

            assertEquals(RefreshTokenService.INVALID_SESSION, ex.getMessage());
            verify(refreshTokenRepository).delete(token);
            verify(refreshTokenRepository, never()).revokeIfActive(any());
            verify(sessionRevocationService, never()).endSessions(any());
        }

        @Test
        @DisplayName("a session not renewed for longer than the idle timeout has ended: deleted, no replay")
        void shouldEndAnIdleSession() {
            ReflectionTestUtils.setField(refreshTokenService, "refreshIdleTimeoutMs", 30 * 60_000L);
            RefreshToken token = active("idle-token", Users.builder().username("away").build(),
                    Instant.now().plusSeconds(3600));
            token.setCreatedAt(Instant.now().minusSeconds(31 * 60));

            RefreshTokenException ex = assertThrows(RefreshTokenException.class,
                    () -> refreshTokenService.refreshAccessToken("idle-token"));

            assertEquals(RefreshTokenService.INVALID_SESSION, ex.getMessage());
            verify(refreshTokenRepository).delete(token);
            verify(refreshTokenRepository, never()).revokeIfActive(any());
            verify(sessionRevocationService, never()).endSessions(any());
        }
    }

    @Nested
    @DisplayName("Unit Test: RefreshTokenService replay")
    class Replay {

        @Test
        @DisplayName("a rotated token presented again ends every session of the account and is recorded")
        void shouldEndSessionsWhenTokenRevoked() {
            Users user = Users.builder().username("victim").build();
            active("revoked-token", user, Instant.now().plusSeconds(3600)).setRevoked(true);

            RefreshTokenException ex = assertThrows(RefreshTokenException.class,
                    () -> refreshTokenService.refreshAccessToken("revoked-token"));

            assertEquals(RefreshTokenService.INVALID_SESSION, ex.getMessage());
            verify(sessionRevocationService).sessionsCompromised(user);
            verify(securityEvents).recordFor(SecurityEvents.Type.REFRESH_REUSE, "victim", null, null);
        }

        @Test
        @DisplayName("of two refreshes with the same token at once, the one losing the race is a replay")
        void lostRaceIsAReplay() {
            Users user = Users.builder().username("victim").build();
            RefreshToken token = active("raced-token", user, Instant.now().plusSeconds(3600));
            when(refreshTokenRepository.revokeIfActive(token.getId())).thenReturn(0);

            assertThrows(RefreshTokenException.class, () -> refreshTokenService.refreshAccessToken("raced-token"));

            verify(sessionRevocationService).sessionsCompromised(user);
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("a replay on the shared demo account is recorded but ends no other visitor's session")
        void demoReplayEndsNoOtherSession() {
            active("demo-token", Users.builder().username("demo").build(), Instant.now().plusSeconds(3600))
                    .setRevoked(true);
            when(publicDemoGuard.isPublicDemo("demo")).thenReturn(true);

            assertThrows(RefreshTokenException.class, () -> refreshTokenService.refreshAccessToken("demo-token"));

            verify(securityEvents).recordFor(SecurityEvents.Type.REFRESH_REUSE, "demo", null, null);
            verify(sessionRevocationService, never()).sessionsCompromised(any());
            verify(sessionRevocationService, never()).endSessions(any());
        }
    }

    @Nested
    @DisplayName("Unit Test: RefreshTokenService logout and cleanup")
    class Logout {

        @Test
        @DisplayName("a logout ends the account's session, the access token included")
        void shouldEndSessionsOnLogout() {
            Users user = Users.builder().username("leaving").build();
            active("to-revoke", user, Instant.now().plusSeconds(3600));

            refreshTokenService.revokeToken("to-revoke");

            verify(sessionRevocationService).endSessions(user);
        }

        @Test
        @DisplayName("a logout on the shared demo account deletes only the visitor's own token")
        void demoLogoutEndsOnlyItsOwnSession() {
            RefreshToken token = active("demo-token", Users.builder().username("demo").build(),
                    Instant.now().plusSeconds(3600));
            when(publicDemoGuard.isPublicDemo("demo")).thenReturn(true);

            refreshTokenService.revokeToken("demo-token");

            verify(refreshTokenRepository).delete(token);
            verify(sessionRevocationService, never()).endSessions(any());
        }

        @Test
        @DisplayName("a logout with a token already rotated away changes nothing")
        void logoutWithARotatedTokenChangesNothing() {
            active("rotated", Users.builder().username("old").build(), Instant.now().plusSeconds(3600))
                    .setRevoked(true);

            refreshTokenService.revokeToken("rotated");

            verify(sessionRevocationService, never()).endSessions(any());
            verify(refreshTokenRepository, never()).delete(any());
        }

        @Test
        @DisplayName("a logout with an unknown token changes nothing")
        void shouldDoNothingWhenTokenNotFoundOnRevoke() {
            when(refreshTokenRepository.findByToken(sha256("ghost"))).thenReturn(Optional.empty());

            refreshTokenService.revokeToken("ghost");

            verify(sessionRevocationService, never()).endSessions(any());
        }

        @Test
        @DisplayName("the nightly cleanup drops tokens 30 days past their expiry")
        void nightlyCleanup() {
            ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);

            refreshTokenService.deleteExpiredTokens();

            verify(refreshTokenRepository).deleteExpiredBefore(cutoff.capture());
            Instant expected = Instant.now().minus(RefreshTokenService.KEEP_AFTER_EXPIRY);
            assertTrue(Math.abs(cutoff.getValue().getEpochSecond() - expected.getEpochSecond()) < 5);
        }
    }

    @Nested
    @DisplayName("Unit Test: RefreshTokenService hashing")
    class Hashing {

        @Test
        @DisplayName("hashToken produces consistent 64-char hex for the same input")
        void hashTokenIsConsistentAndHexEncoded() {
            String h1 = RefreshTokenService.hashToken("test-input");
            assertEquals(h1, RefreshTokenService.hashToken("test-input"));
            assertTrue(h1.matches("[0-9a-f]{64}"));
        }

        @Test
        @DisplayName("hashToken produces different hashes for different inputs")
        void hashTokenIsDifferentForDifferentInputs() {
            assertNotEquals(RefreshTokenService.hashToken("token-a"), RefreshTokenService.hashToken("token-b"));
        }

        @Test
        @DisplayName("hashToken wraps NoSuchAlgorithmException in an IllegalStateException")
        void hashTokenWrapsNoSuchAlgorithmException() {
            try (MockedStatic<MessageDigest> mocked = mockStatic(MessageDigest.class)) {
                mocked.when(() -> MessageDigest.getInstance("SHA-256"))
                        .thenThrow(new NoSuchAlgorithmException("SHA-256"));

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                        () -> RefreshTokenService.hashToken("any"));
                assertEquals("SHA-256 not available", ex.getMessage());
            }
        }
    }
}
