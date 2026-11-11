/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.service.RefreshTokenService;

/**
 * The one place that decides what a proven first factor gets: the password at
 * login, or the emailed code that confirms a new organization's first admin.
 * Both go through here, so neither can hand an admin a session without 2FA.
 */
@Component
public class SessionIssuer {

    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final TwoFactorPolicy twoFactorPolicy;

    public SessionIssuer(JwtUtils jwtUtils, RefreshTokenService refreshTokenService, TwoFactorPolicy twoFactorPolicy) {
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.twoFactorPolicy = twoFactorPolicy;
    }

    public LoginOutcome afterFirstFactor(Users user) {
        if (user.isTotpEnabled()) {
            return new LoginOutcome(null, jwtUtils.generateMfaPendingToken(user));
        }
        if (twoFactorPolicy.blocksSession(user)) {
            return new LoginOutcome(null, null, jwtUtils.generateMfaSetupToken(user));
        }
        return new LoginOutcome(sessionFor(user), null);
    }

    /** A real session: an access token and a refresh token. */
    public AuthResponseDTO sessionFor(Users user) {
        return new AuthResponseDTO(jwtUtils.generateToken(user), refreshTokenService.createRefreshToken(user));
    }
}
