/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.util;

import java.security.KeyPair;
import java.util.Base64;

import io.jsonwebtoken.Jwts;

/**
 * Utility class for generating the RSA key pair used to sign the internal
 * {@code X-Internal-Claims} JWT that MlProxyService attaches to every call
 * to the ML (FastAPI) service, asserting the caller's org_id/manager_id.
 * <p>
 * Asymmetric on purpose: the backend holds the private key and signs, the ML
 * service holds only the public key and verifies — compromising the ML
 * container's environment does not grant the ability to forge a different
 * org_id/manager_id, unlike a shared-secret (HMAC) scheme would.
 * <p>
 * Run this utility once to obtain a key pair, then set
 * {@code ml.claims.private-key} (backend) and the FastAPI service's
 * {@code INTERNAL_CLAIMS_PUBLIC_KEY} (ml) from its output. It prints to
 * standard output, never through a logger: a private key must not reach a
 * log file or a log collector (OWASP JWT Cheat Sheet).
 */
public final class InternalClaimsKeyGenerator {

    private InternalClaimsKeyGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** A new RS256 key pair: the private key (PKCS8) and the public one (X.509), base64-encoded. */
    static String[] generate() {
        KeyPair keyPair = Jwts.SIG.RS256.keyPair().build();
        Base64.Encoder base64 = Base64.getEncoder();
        return new String[] {
            base64.encodeToString(keyPair.getPrivate().getEncoded()),
            base64.encodeToString(keyPair.getPublic().getEncoded())
        };
    }

    @SuppressWarnings("java:S106") // the console is the point: see the class comment
    public static void main(String[] args) {
        String[] keys = generate();
        System.out.println("ML claims private key (PKCS8, base64) — set as ml.claims.private-key on the BACKEND only:");
        System.out.println(keys[0]);
        System.out.println("ML claims public key (X.509, base64) — set as INTERNAL_CLAIMS_PUBLIC_KEY on the ML service:");
        System.out.println(keys[1]);
    }
}
