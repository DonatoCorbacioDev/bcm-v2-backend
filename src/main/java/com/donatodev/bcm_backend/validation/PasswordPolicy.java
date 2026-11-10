package com.donatodev.bcm_backend.validation;

import java.nio.charset.StandardCharsets;

/**
 * The password rule as plain checks, shared by {@link PasswordValidator} and
 * by code that sets a password without a validated request body.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** BCrypt reads at most 72 bytes; Spring Security refuses a longer password. */
    public static final int MAX_BYTES = 72;

    public static final String REQUIRED = "Password obbligatoria";
    public static final String TOO_SHORT = "La password deve contenere almeno " + MIN_LENGTH + " caratteri";
    public static final String TOO_LONG =
            "La password è troppo lunga (massimo " + MAX_BYTES + " byte: le lettere accentate ne occupano 2)";

    private PasswordPolicy() {
    }

    /** The message for the first rule the password breaks, or null when it follows them all. */
    public static String violation(String password) {
        if (password == null || password.isBlank()) {
            return REQUIRED;
        }
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            return TOO_SHORT;
        }
        if (exceedsMaxBytes(password)) {
            return TOO_LONG;
        }
        return null;
    }

    /** Whether BCrypt would refuse to hash it. */
    public static boolean exceedsMaxBytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }

    /**
     * Checks a password about to be hashed.
     *
     * @throws IllegalArgumentException with the Italian message of the rule it breaks
     */
    public static void require(String password) {
        String violation = violation(password);
        if (violation != null) {
            throw new IllegalArgumentException(violation);
        }
    }
}
