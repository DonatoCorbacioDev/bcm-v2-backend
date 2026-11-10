package com.donatodev.bcm_backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * How single-use secrets sent to people are stored (OWASP API2): refresh
 * tokens, password reset links, invitation links. The database keeps only the
 * SHA-256 of the token, so whoever reads it (a dump, a stolen backup) holds
 * nothing that works as a link. A random token needs no salt: there is no
 * dictionary to guess it from.
 *
 * <p>Lowercase hex, the same as MySQL's {@code SHA2(token, 256)}: V59 hashed
 * the links already sent with it, and they keep working.
 */
public final class TokenHash {

    private TokenHash() {
    }

    public static String sha256Hex(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
