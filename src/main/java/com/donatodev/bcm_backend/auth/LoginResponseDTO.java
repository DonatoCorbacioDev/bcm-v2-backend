/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

/**
 * Response for POST /auth/login, POST /auth/2fa/verify and the confirmation
 * of a new organization.
 * <p>
 * When {@code mfaRequired} is true, {@code token} is null and the client
 * must call /auth/2fa/verify with {@code mfaToken} plus a TOTP or recovery
 * code to obtain the real access token. When {@code mfaSetupRequired} is
 * true, the account must set 2FA up first: /auth/2fa/enroll/setup and
 * /auth/2fa/enroll/confirm with {@code setupToken}.
 */
public record LoginResponseDTO(String token, boolean mfaRequired, String mfaToken,
        boolean mfaSetupRequired, String setupToken) {

    public LoginResponseDTO(String token, boolean mfaRequired, String mfaToken) {
        this(token, mfaRequired, mfaToken, false, null);
    }

    /** Only the setup token: no session yet. */
    public static LoginResponseDTO setupRequired(String setupToken) {
        return new LoginResponseDTO(null, false, null, true, setupToken);
    }
}
