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

    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");
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
}
