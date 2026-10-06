/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.util;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Structural + ISO 7064 mod-97 checksum validation for IBANs.
 * Deliberately does not enforce per-country exact lengths: the mod-97
 * checksum already catches virtually all real-world typos.
 */
public final class IbanValidator {

    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}\\d{2}[A-Z0-9]{11,30}$");
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private IbanValidator() {
    }

    public static boolean isValid(String iban) {
        if (iban == null) {
            return false;
        }
        String normalized = iban.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!IBAN_PATTERN.matcher(normalized).matches()) {
            return false;
        }

        String rearranged = normalized.substring(4) + normalized.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rearranged.length() * 2);
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) {
                numeric.append(c);
            } else {
                numeric.append(Character.getNumericValue(c));
            }
        }

        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }

    /**
     * Partially masks an IBAN for audit-log/log messages: country code plus
     * the last 4 characters, e.g. {@code "IT...3456"}. Never logs a full IBAN
     * in plaintext while still letting someone recognize which account changed.
     */
    public static String mask(String iban) {
        if (iban == null || iban.isBlank()) {
            return "none";
        }
        String normalized = iban.replace(" ", "");
        if (normalized.length() <= 6) {
            return "***";
        }
        return normalized.substring(0, 2) + "..." + normalized.substring(normalized.length() - 4);
    }

    /**
     * Same masking as {@link #mask(String)}, but preserves {@code null}
     * instead of turning it into the string {@code "none"}. Audit-log prose
     * reads better with "none"; an API response field must not -- UI code
     * (e.g. "IBAN mancante" badges) checks this field's presence, and a
     * non-null placeholder string would read as "present" when it isn't.
     */
    public static String maskNullable(String iban) {
        return iban == null ? null : mask(iban);
    }
}
