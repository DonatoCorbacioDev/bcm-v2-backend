/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

/**
 * Result of {@link AuthService#authenticate}. Exactly one of the two fields
 * is populated: {@code tokens} for a completed login, or {@code mfaToken}
 * when the account has 2FA enabled and a second step is required.
 */
public record LoginOutcome(AuthResponseDTO tokens, String mfaToken) {

    public boolean mfaRequired() {
        return mfaToken != null;
    }
}
