/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

import java.util.Base64;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Validates security-critical configuration at startup in production.
 * Fails fast rather than silently running with weak, known or missing secrets
 * (OWASP Secrets Management Cheat Sheet). The messages name the variable,
 * never its value.
 */
@Component
@Profile("prod")
public class SecurityStartupValidator implements ApplicationRunner {

    /** Placeholder values published in this project's own files: never a real key. */
    static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "your-super-secret-jwt-key-change-in-production-min-256-bits",
            "your_base64_encoded_jwt_secret_key_min_256_bits",
            "generate_your_own_base64_key_here",
            "ci-secret-key-for-validation-only-not-real-min-256-bits");

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.previous-secret:}")
    private String previousJwtSecret;

    @Value("${totp.encryption-key:}")
    private String totpEncryptionKey;

    @Value("${ml.internal-api-key:}")
    private String mlInternalApiKey;

    @Value("${ml.fastapi.url:http://localhost:8000}")
    private String mlFastapiUrl;

    @Override
    public void run(ApplicationArguments args) {
        validateJwtSecret();
        validatePreviousJwtSecret();
        validateTotpEncryptionKey();
        validateMlApiKey();
    }

    private void validateJwtSecret() {
        if (jwtSecret == null || KNOWN_PLACEHOLDERS.contains(jwtSecret.trim())) {
            throw new IllegalStateException("JWT_SECRET is a placeholder from the project's files: set a real key.");
        }
        if (decodedLength(jwtSecret, "JWT_SECRET") < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least 256 bits (32 bytes). " +
                    "Generate a secure key with the bundled JwtKeyGenerator utility.");
        }
    }

    private void validatePreviousJwtSecret() {
        if (previousJwtSecret == null || previousJwtSecret.isBlank()) {
            return;
        }
        if (previousJwtSecret.trim().equals(jwtSecret.trim())) {
            throw new IllegalStateException("JWT_SECRET_PREVIOUS is the same as JWT_SECRET: leave it empty.");
        }
        if (decodedLength(previousJwtSecret, "JWT_SECRET_PREVIOUS") < 32) {
            throw new IllegalStateException("JWT_SECRET_PREVIOUS must decode to at least 256 bits (32 bytes).");
        }
    }

    private void validateTotpEncryptionKey() {
        if (totpEncryptionKey == null || totpEncryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "TOTP_ENCRYPTION_KEY must be set in production (32 bytes in Base64): "
                    + "two-factor secrets must not be encrypted under a key derived from JWT_SECRET.");
        }
        if (decodedLength(totpEncryptionKey, "TOTP_ENCRYPTION_KEY") != 32) {
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256).");
        }
    }

    private void validateMlApiKey() {
        boolean mlExposed = !mlFastapiUrl.contains("localhost") && !mlFastapiUrl.contains("127.0.0.1");
        if (mlExposed && (mlInternalApiKey == null || mlInternalApiKey.isBlank())) {
            throw new IllegalStateException(
                    "ML_INTERNAL_API_KEY must be set when ML_FASTAPI_URL points to a non-local host. " +
                    "The ML service would otherwise accept unauthenticated requests.");
        }
    }

    private static int decodedLength(String value, String name) {
        try {
            return Base64.getDecoder().decode(value.trim()).length;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " must be Base64.", e);
        }
    }
}
