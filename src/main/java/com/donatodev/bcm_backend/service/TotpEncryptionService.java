/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encrypts TOTP secrets at rest with AES-256-GCM, under a key of their own
 * ({@code TOTP_ENCRYPTION_KEY}, 32 bytes in Base64), never shared with
 * anything else (OWASP Secrets Management Cheat Sheet): rotating the JWT
 * signing key must not lock out every account with two-factor on.
 * <p>
 * A value encrypted with that key is stored as {@code "v2:" + Base64(iv | ciphertext)}.
 * Values stored before it existed have no prefix and were encrypted under a
 * key derived from the JWT secret (SHA-256 of it): they are still read, with
 * the current JWT secret or the previous one during a rotation, and
 * {@link TotpKeyMigration} re-encrypts them once under the dedicated key.
 * Without the dedicated key (a laptop, never production: see
 * SecurityStartupValidator) new values keep the old format.
 * <p>
 * Rotating the dedicated key (OWASP Database Security, key rotation): the old
 * one becomes {@code TOTP_ENCRYPTION_KEY_PREVIOUS} and a new one
 * {@code TOTP_ENCRYPTION_KEY}. Values are read with either, and
 * {@link TotpKeyMigration} re-encrypts those only the previous key opens, once,
 * at startup; then the previous key is removed.
 */
@Service
public final class TotpEncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(TotpEncryptionService.class);

    /** Marks a value encrypted with the dedicated key. */
    static final String PREFIX = "v2:";
    private static final int KEY_BYTES = 32;

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String AES_ALGORITHM = "AES";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** The dedicated key, or null when none is configured. */
    private final SecretKeySpec key;
    /** The dedicated key before the current one, during a rotation, or null. */
    private final SecretKeySpec previousKey;
    /** The key of values stored before the dedicated one, derived from the JWT secret. */
    private final SecretKeySpec legacyKey;
    /** The same, from the previous JWT secret during a rotation, or null. */
    private final SecretKeySpec legacyPreviousKey;

    public TotpEncryptionService(String encryptionKey, String jwtSecret, String previousJwtSecret) {
        this(encryptionKey, "", jwtSecret, previousJwtSecret);
    }

    @Autowired
    public TotpEncryptionService(@Value("${totp.encryption-key:}") String encryptionKey,
                                 @Value("${totp.encryption-key-previous:}") String previousEncryptionKey,
                                 @Value("${jwt.secret}") String jwtSecret,
                                 @Value("${jwt.previous-secret:}") String previousJwtSecret) {
        this.key = dedicatedKey(encryptionKey, "TOTP_ENCRYPTION_KEY");
        this.previousKey = dedicatedKey(previousEncryptionKey, "TOTP_ENCRYPTION_KEY_PREVIOUS");
        this.legacyKey = derivedKey(jwtSecret);
        this.legacyPreviousKey = previousJwtSecret == null || previousJwtSecret.isBlank()
                ? null : derivedKey(previousJwtSecret);
        if (key == null) {
            logger.warn("TOTP_ENCRYPTION_KEY is not set: two-factor secrets are encrypted under a key "
                    + "derived from the JWT secret (development only)");
        }
    }

    /** Whether the dedicated key is configured. */
    public boolean hasDedicatedKey() {
        return key != null;
    }

    /**
     * Whether a stored value should be re-encrypted under the current key: it is
     * still in the old format, or, during a rotation, only the previous key opens it.
     */
    public boolean needsReEncryption(String stored) {
        if (key == null || stored == null) {
            return false;
        }
        if (!stored.startsWith(PREFIX)) {
            return true;
        }
        if (previousKey == null) {
            return false;
        }
        try {
            decryptWith(key, stored.substring(PREFIX.length()));
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public String encrypt(String plainText) {
        if (key == null) {
            return encryptWith(legacyKey, plainText);
        }
        return PREFIX + encryptWith(key, plainText);
    }

    public String decrypt(String stored) {
        if (stored.startsWith(PREFIX)) {
            if (key == null) {
                throw new IllegalStateException("TOTP secret encrypted with TOTP_ENCRYPTION_KEY, which is not set");
            }
            String encoded = stored.substring(PREFIX.length());
            try {
                return decryptWith(key, encoded);
            } catch (IllegalStateException e) {
                if (previousKey == null) {
                    throw e;
                }
                return decryptWith(previousKey, encoded);
            }
        }
        try {
            return decryptWith(legacyKey, stored);
        } catch (IllegalStateException e) {
            if (legacyPreviousKey == null) {
                throw e;
            }
            return decryptWith(legacyPreviousKey, stored);
        }
    }

    private static String encryptWith(SecretKeySpec secretKey, String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // iv | ciphertext, written one after the other: no length arithmetic
            // (CodeQL java/uncontrolled-arithmetic flagged the sum of the two).
            ByteArrayOutputStream combined = new ByteArrayOutputStream();
            combined.writeBytes(iv);
            combined.writeBytes(cipherText);
            return Base64.getEncoder().encodeToString(combined.toByteArray());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", e);
        }
    }

    private static String decryptWith(SecretKeySpec secretKey, String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to decrypt TOTP secret", e);
        }
    }

    private static SecretKeySpec dedicatedKey(String encoded, String name) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " must be Base64", e);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(name + " must decode to exactly 32 bytes (AES-256)");
        }
        return new SecretKeySpec(bytes, AES_ALGORITHM);
    }

    private static SecretKeySpec derivedKey(String jwtSecret) {
        try {
            byte[] rawSecret = Base64.getDecoder().decode(jwtSecret);
            return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(rawSecret), AES_ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
