/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

/**
 * What a correct password leads to ({@link SessionIssuer}). Exactly one field
 * is populated: {@code tokens} for a completed login, {@code mfaToken} when
 * the account has 2FA on and the code comes next, {@code setupToken} when the
 * account must set 2FA up before getting a session.
 */
public record LoginOutcome(AuthResponseDTO tokens, String mfaToken, String setupToken) {

    public LoginOutcome(AuthResponseDTO tokens, String mfaToken) {
        this(tokens, mfaToken, null);
    }

    public boolean mfaRequired() {
        return mfaToken != null;
    }

    public boolean mfaSetupRequired() {
        return setupToken != null;
    }
}
