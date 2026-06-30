package com.donatodev.bcm_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    public String createRefreshToken(Users user) {
        refreshTokenRepository.deleteAllByUser(user);
        return issueToken(user).rawToken();
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
        RefreshToken refreshToken = refreshTokenRepository.findByToken(hashToken(tokenValue))
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

        IssuedToken newToken = issueToken(refreshToken.getUser());
        String newAccessToken = jwtUtils.generateToken(refreshToken.getUser());

        return new RotatedTokens(newAccessToken, newToken.rawToken());
    }

    private IssuedToken issueToken(Users user) {
        String rawToken = UUID.randomUUID().toString();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(hashToken(rawToken))
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();
        return new IssuedToken(rawToken, refreshTokenRepository.save(token));
    }

    public record RotatedTokens(String accessToken, String refreshToken) {}

    private record IssuedToken(String rawToken, RefreshToken entity) {}

    @Transactional
    public void revokeToken(String tokenValue) {
        refreshTokenRepository.findByToken(hashToken(tokenValue)).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
