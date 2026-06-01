package com.donatodev.bcm_backend.service;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: RefreshTokenService")
    @SuppressWarnings("unused")
    class VerifyRefreshTokenService {

        @Test
        @Order(1)
        @DisplayName("createRefreshToken returns a saved token")
        void shouldCreateRefreshToken() {
            Users user = Users.builder().username("testuser").build();
            RefreshToken saved = RefreshToken.builder().token("uuid-token").user(user)
                    .expiryDate(Instant.now().plusSeconds(600)).build();

            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(saved);

            RefreshToken result = refreshTokenService.createRefreshToken(user);

            assertNotNull(result);
            assertEquals("uuid-token", result.getToken());
            verify(refreshTokenRepository).deleteAllByUser(user);
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @Order(2)
        @DisplayName("refreshAccessToken returns new access token")
        void shouldRefreshAccessToken() {
            Users user = Users.builder().username("testuser").build();
            RefreshToken token = RefreshToken.builder()
                    .token("valid-token")
                    .user(user)
                    .expiryDate(Instant.now().plusSeconds(600))
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
            when(jwtUtils.generateToken(user)).thenReturn("new-access-token");

            String result = refreshTokenService.refreshAccessToken("valid-token");

            assertEquals("new-access-token", result);
        }

        @Test
        @Order(3)
        @DisplayName("refreshAccessToken throws when token not found")
        void shouldThrowWhenTokenNotFound() {
            when(refreshTokenRepository.findByToken("ghost")).thenReturn(Optional.empty());

            assertThrows(RefreshTokenException.class,
                    () -> refreshTokenService.refreshAccessToken("ghost"));
        }

        @Test
        @Order(4)
        @DisplayName("refreshAccessToken throws when token is revoked")
        void shouldThrowWhenTokenRevoked() {
            RefreshToken token = RefreshToken.builder()
                    .token("revoked-token")
                    .user(Users.builder().build())
                    .expiryDate(Instant.now().plusSeconds(600))
                    .revoked(true)
                    .build();

            when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(token));

            assertThrows(RefreshTokenException.class,
                    () -> refreshTokenService.refreshAccessToken("revoked-token"));
        }

        @Test
        @Order(5)
        @DisplayName("refreshAccessToken throws and marks revoked when token expired")
        void shouldThrowAndRevokeWhenTokenExpired() {
            RefreshToken token = RefreshToken.builder()
                    .token("expired-token")
                    .user(Users.builder().build())
                    .expiryDate(Instant.now().minusSeconds(60))
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));
            when(refreshTokenRepository.save(any())).thenReturn(token);

            assertThrows(RefreshTokenException.class,
                    () -> refreshTokenService.refreshAccessToken("expired-token"));

            verify(refreshTokenRepository).save(token);
        }

        @Test
        @Order(6)
        @DisplayName("revokeToken marks token as revoked")
        void shouldRevokeToken() {
            RefreshToken token = RefreshToken.builder()
                    .token("to-revoke")
                    .user(Users.builder().build())
                    .expiryDate(Instant.now().plusSeconds(600))
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByToken("to-revoke")).thenReturn(Optional.of(token));
            when(refreshTokenRepository.save(any())).thenReturn(token);

            refreshTokenService.revokeToken("to-revoke");

            verify(refreshTokenRepository).save(token);
        }

        @Test
        @Order(7)
        @DisplayName("revokeToken does nothing when token not found")
        void shouldDoNothingWhenTokenNotFoundOnRevoke() {
            when(refreshTokenRepository.findByToken("ghost")).thenReturn(Optional.empty());

            refreshTokenService.revokeToken("ghost");

            verify(refreshTokenRepository).findByToken("ghost");
        }
    }
}
