/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.util;

import java.util.Base64;

import io.jsonwebtoken.Jwts;

/**
 * Run-once command-line tool that prints a new {@code JWT_SECRET}: a random
 * 384-bit key, base64-encoded, for the HS384 the backend signs with
 * ({@link com.donatodev.bcm_backend.jwt.JwtUtils#algorithmFor}).
 * <p>
 * It prints to standard output, never through a logger: a secret must not
 * reach a log file or a log collector (OWASP JWT Cheat Sheet).
 */
public final class JwtKeyGenerator {

    private JwtKeyGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** A new key for HS384, base64-encoded. */
    static String generate() {
        return Base64.getEncoder().encodeToString(Jwts.SIG.HS384.key().build().getEncoded());
    }

    @SuppressWarnings("java:S106") // the console is the point: see the class comment
    public static void main(String[] args) {
        System.out.println("JWT_SECRET (HS384, base64):");
        System.out.println(generate());
    }
}
