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

    /**
     * Database passwords of development, CI and tests, all in this project's
     * files (OWASP Database Security: development and production credentials
     * kept apart): production never runs with one.
     */
    static final Set<String> DEVELOPMENT_DB_PASSWORDS = Set.of(
            "bcm_password", "ci-password", "ci-app-password", "bcm_it_password", "bcm_it_app_password",
            "your_db_password_here", "your_root_password_here");

    /** Production's are random, 31 to 48 characters; anything this short was typed by hand. */
    static final int MIN_DB_PASSWORD_LENGTH = 20;

    @Value("${spring.datasource.url:}")
    private String databaseUrl;

    @Value("${spring.datasource.password:}")
    private String databasePassword;

    @Value("${redis.password:}")
    private String redisPassword;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.previous-secret:}")
    private String previousJwtSecret;

    @Value("${totp.encryption-key:}")
    private String totpEncryptionKey;

    @Value("${totp.encryption-key-previous:}")
    private String previousTotpEncryptionKey;

    @Value("${ml.internal-api-key:}")
    private String mlInternalApiKey;

    @Value("${ml.fastapi.url:http://localhost:8000}")
    private String mlFastapiUrl;

    @Override
    public void run(ApplicationArguments args) {
        validateJwtSecret();
        validatePreviousJwtSecret();
        validateTotpEncryptionKey();
        validatePreviousTotpEncryptionKey();
        validateMlApiKey();
        validateDatabase();
        validateRedis();
    }

    /**
     * Redis's password (OWASP Database Security, authentication): without it the
     * backend would use Redis's default user, which in production may only PING
     * (bcm-v2-docker/ops/redis/create-acl.sh), and any container on the data
     * network could do as much as the backend.
     */
    private void validateRedis() {
        if (redisPassword == null || redisPassword.trim().length() < MIN_DB_PASSWORD_LENGTH) {
            throw new IllegalStateException("REDIS_PASSWORD must be set in production (at least "
                    + MIN_DB_PASSWORD_LENGTH + " characters): run bcm-v2-docker/ops/redis/create-acl.sh.");
        }
    }

    /**
     * The database account's password, and TLS to the database (OWASP Database
     * Security, authentication and secure connections): a development password,
     * or a connection that is not encrypted with the server's certificate
     * checked, its CA and its name (sslMode=VERIFY_IDENTITY), stops the backend
     * instead of running so.
     */
    private void validateDatabase() {
        if (databasePassword == null || DEVELOPMENT_DB_PASSWORDS.contains(databasePassword.trim())) {
            throw new IllegalStateException("The database password is a development one from the project's files.");
        }
        if (databasePassword.trim().length() < MIN_DB_PASSWORD_LENGTH) {
            throw new IllegalStateException("The database password must be at least " + MIN_DB_PASSWORD_LENGTH
                    + " characters (ops/mysql/create-app-runtime-user.sh generates one).");
        }
        String url = databaseUrl == null ? "" : databaseUrl.toLowerCase(java.util.Locale.ROOT);
        boolean verified = url.contains("sslmode=verify_identity");
        if (!verified || url.contains("allowpublickeyretrieval=true") || url.contains("usessl=false")) {
            throw new IllegalStateException("The database connection must use TLS with the server's certificate "
                    + "and name verified (sslMode=VERIFY_IDENTITY, no allowPublicKeyRetrieval): "
                    + "see docker-compose.prod.yml.");
        }
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

    /** During a rotation only: a real, different key, which nothing else may be. */
    private void validatePreviousTotpEncryptionKey() {
        if (previousTotpEncryptionKey == null || previousTotpEncryptionKey.isBlank()) {
            return;
        }
        if (previousTotpEncryptionKey.trim().equals(totpEncryptionKey == null ? "" : totpEncryptionKey.trim())) {
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY_PREVIOUS is the same as TOTP_ENCRYPTION_KEY: leave it empty.");
        }
        if (decodedLength(previousTotpEncryptionKey, "TOTP_ENCRYPTION_KEY_PREVIOUS") != 32) {
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY_PREVIOUS must decode to exactly 32 bytes (AES-256).");
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
