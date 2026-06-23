package com.donatodev.bcm_backend.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.entity.RefreshToken;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.RefreshTokenException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;

@Service
public class RefreshTokenService {

    @Value("${jwt.refreshExpirationMs:604800000}")
    private long refreshExpirationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtils jwtUtils;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtUtils jwtUtils) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public RefreshToken createRefreshToken(Users user) {
        refreshTokenRepository.deleteAllByUser(user);
        return issueToken(user);
    }

    /**
     * Rotates the refresh token on every use: the presented token is revoked
     * and a brand new one is issued alongside the new access token. If a
     * caller presents a token that is already revoked, that token has either
     * expired naturally or already been rotated away - presenting it again
     * means it was stolen and replayed, so every refresh token belonging to
     * the user is revoked to force re-authentication on all sessions.
     */
    @Transactional(noRollbackFor = RefreshTokenException.class)
    public RotatedTokens refreshAccessToken(String tokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new RefreshTokenException("Refresh token not found"));

        if (refreshToken.isRevoked()) {
            refreshTokenRepository.revokeAllByUser(refreshToken.getUser());
            throw new RefreshTokenException("Refresh token reuse detected; all sessions revoked");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new RefreshTokenException("Refresh token has expired");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        RefreshToken newRefreshToken = issueToken(refreshToken.getUser());
        String newAccessToken = jwtUtils.generateToken(refreshToken.getUser());

        return new RotatedTokens(newAccessToken, newRefreshToken.getToken());
    }

    private RefreshToken issueToken(Users user) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(token);
    }

    public record RotatedTokens(String accessToken, String refreshToken) {}

    @Transactional
    public void revokeToken(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }
}
