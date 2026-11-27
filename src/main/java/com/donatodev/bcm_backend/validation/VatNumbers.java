package com.donatodev.bcm_backend.validation;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The shape of a VAT number (Partita IVA). An Italian one, with or without
 * "IT" in front, has 11 digits and its last digit is a check digit computed
 * from the other ten (the Agenzia delle Entrate's algorithm, the Luhn
 * scheme): a typo in one digit, or a made-up number, fails it. A foreign one
 * (a supplier in Germany: "DE123456789") is two letters and 2 to 13 letters
 * or digits; its own country's rules are not checked here.
 */
public final class VatNumbers {

    public static final String INVALID =
            "Partita IVA non valida: una partita IVA italiana ha 11 cifre (es. IT01234567897), una estera inizia con la sigla del paese";

    private static final Pattern ITALIAN = Pattern.compile("^(IT)?\\d+$");
    private static final Pattern FOREIGN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{2,13}$");

    private VatNumbers() {
    }

    /** Spaces removed and upper case: "it 0123..." and "IT0123..." are the same number. */
    public static String normalize(String raw) {
        return raw == null ? null : raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /** True for a well-formed Italian or foreign VAT number; blank is not one. */
    public static boolean isValid(String raw) {
        String vat = normalize(raw);
        if (vat == null || vat.isEmpty()) {
            return false;
        }
        if (ITALIAN.matcher(vat).matches()) {
            return isValidItalian(vat.startsWith("IT") ? vat.substring(2) : vat);
        }
        return FOREIGN.matcher(vat).matches();
    }

    private static boolean isValidItalian(String digits) {
        if (digits.length() != 11 || digits.chars().allMatch(c -> c == '0')) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            int d = digits.charAt(i) - '0';
            // Every second digit (the 2nd, 4th...) is doubled.
            if (i % 2 != 0) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
        }
        return (10 - sum % 10) % 10 == digits.charAt(10) - '0';
    }
}
