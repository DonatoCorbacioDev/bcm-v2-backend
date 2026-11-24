/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.auth.TwoFactorPolicy;
import com.donatodev.bcm_backend.entity.RefreshToken;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.RefreshTokenException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.security.PublicDemoGuard;
import com.donatodev.bcm_backend.auth.NewDeviceMonitor;
import com.donatodev.bcm_backend.security.SecurityEvents;
import com.donatodev.bcm_backend.util.TokenHash;

/**
 * Refresh tokens, after the OWASP Authentication and Session Management Cheat Sheet: 256 random bits, stored
 * only as a hash; rotated on every use, a session ending at the latest
 * {@code jwt.refreshExpirationMs} after the login however often it is
 * renewed; one session per account, so a login or a logout ends the others;
 * and a token presented again after its rotation ends every session of the
 * account, the access tokens included. Revoked means rotated away and
 * nothing else; a session ended otherwise has its token deleted.
 * <p>
 * The public demo account is shared by every visitor: there each visitor
 * keeps their own session, and a logout or a replay touches only that one.
 */
@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    /** The only answer to a refresh refused for the token itself: the cause goes to the audit. */
    public static final String INVALID_SESSION = "Sessione non valida o scaduta: accedi di nuovo";
    /** Revoked tokens are kept this long past their expiry, so a replay is still recognised. */
    static final Duration KEEP_AFTER_EXPIRY = Duration.ofDays(30);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${jwt.refreshExpirationMs:43200000}")
    private long refreshExpirationMs;

    /** How long a session may go without a renewal before it ends (inactivity timeout). */
    @Value("${jwt.refreshIdleTimeoutMs:1800000}")
    private long refreshIdleTimeoutMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtils jwtUtils;
    private final TwoFactorPolicy twoFactorPolicy;
    private final SessionRevocationService sessionRevocationService;
    private final PublicDemoGuard publicDemoGuard;
    private final SecurityEvents securityEvents;
    private final NewDeviceMonitor newDeviceMonitor;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtUtils jwtUtils,
            TwoFactorPolicy twoFactorPolicy, SessionRevocationService sessionRevocationService,
            PublicDemoGuard publicDemoGuard, SecurityEvents securityEvents, NewDeviceMonitor newDeviceMonitor) {
        this.newDeviceMonitor = newDeviceMonitor;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtUtils = jwtUtils;
        this.twoFactorPolicy = twoFactorPolicy;
        this.sessionRevocationService = sessionRevocationService;
        this.publicDemoGuard = publicDemoGuard;
        this.securityEvents = securityEvents;
    }

    /**
     * Opens the session of a login. The account's other live sessions end
     * here, except on the shared demo account; their rotated tokens stay, so
     * a replay of one of them is still recognised.
     */
    @Transactional
    public String createRefreshToken(Users user) {
        if (!isPublicDemo(user)) {
            refreshTokenRepository.deleteActiveByUser(user);
        }
        // A new session, never a renewal: its owner is told if it comes from another device.
        newDeviceMonitor.signedIn(user);
        return issueToken(user, Instant.now().plusMillis(refreshExpirationMs));
    }

    /**
     * Rotates the refresh token: the presented one is revoked and a new one is
     * issued with the new access token, expiring when the presented one did.
     * A token already rotated away presented again means it was stolen and
     * replayed: every session of the account ends. One ended by a logout, a
     * new login or a credential change is gone, and gets only the 401.
     */
    @Transactional(noRollbackFor = RefreshTokenException.class)
    public RotatedTokens refreshAccessToken(String tokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(hashToken(tokenValue))
                .orElseThrow(() -> new RefreshTokenException(INVALID_SESSION));
        Users user = refreshToken.getUser();

        if (refreshToken.isRevoked()) {
            replayed(user);
            throw new RefreshTokenException(INVALID_SESSION);
        }
        if (!refreshToken.getExpiryDate().isAfter(Instant.now())) {
            // Just old, not stolen: removed, so presenting it again is no replay either.
            refreshTokenRepository.delete(refreshToken);
            throw new RefreshTokenException(INVALID_SESSION);
        }
        // Inactivity (OWASP ASVS 7.3.1): each token is issued at a renewal, so
        // its age is the time since the session was last used. Past the idle
        // timeout the session has ended, like an expired one: removed, no replay.
        if (refreshToken.getCreatedAt() != null
                && refreshToken.getCreatedAt().plusMillis(refreshIdleTimeoutMs).isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RefreshTokenException(INVALID_SESSION);
        }
        // Lost a race with another refresh of the same token: one of the two is not its owner.
        if (refreshTokenRepository.revokeIfActive(refreshToken.getId()) == 0) {
            replayed(user);
            throw new RefreshTokenException(INVALID_SESSION);
        }

        // A session opened before 2FA became required for this account (an
        // admin from before the rule, a user just made admin) is not renewed:
        // the next login asks to set 2FA up.
        if (twoFactorPolicy.blocksSession(user)) {
            throw new RefreshTokenException("Configura l'autenticazione a due fattori: accedi di nuovo");
        }

        String newRefreshToken = issueToken(user, refreshToken.getExpiryDate());
        return new RotatedTokens(jwtUtils.generateToken(user), newRefreshToken);
    }

    private void replayed(Users user) {
        securityEvents.recordFor(SecurityEvents.Type.REFRESH_REUSE, user.getUsername(),
                user.getOrganization() == null ? null : user.getOrganization().getId(), null);
        if (!isPublicDemo(user)) {
            sessionRevocationService.endSessions(user);
        }
    }

    /**
     * Logout: ends the session, the access token included. An account has
     * one session at a time, so that is all of them; on the shared demo
     * account only the visitor's own refresh token is deleted.
     */
    @Transactional
    public void revokeToken(String tokenValue) {
        refreshTokenRepository.findByToken(hashToken(tokenValue)).ifPresent(token -> {
            if (token.isRevoked()) {
                return;
            }
            if (isPublicDemo(token.getUser())) {
                refreshTokenRepository.delete(token);
            } else {
                sessionRevocationService.endSessions(token.getUser());
            }
        });
    }

    /** Nightly: tokens past their expiry by {@link #KEEP_AFTER_EXPIRY} are of no further use. */
    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now().minus(KEEP_AFTER_EXPIRY));
        if (deleted > 0) {
            logger.info("Deleted {} expired refresh token(s)", deleted);
        }
    }

    private String issueToken(Users user, Instant expiry) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(hashToken(rawToken))
                .expiryDate(expiry)
                .revoked(false)
                .build());
        return rawToken;
    }

    private boolean isPublicDemo(Users user) {
        return publicDemoGuard.isPublicDemo(user.getUsername());
    }

    public record RotatedTokens(String accessToken, String refreshToken) {}

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    static String hashToken(String rawToken) {
        return TokenHash.sha256Hex(rawToken);
    }
}
