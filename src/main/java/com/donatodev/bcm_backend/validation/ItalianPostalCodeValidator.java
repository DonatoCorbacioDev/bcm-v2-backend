package com.donatodev.bcm_backend.validation;

import java.util.regex.Pattern;

import com.donatodev.bcm_backend.dto.CounterpartyDTO;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link ItalianPostalCode} on a counterparty. */
public class ItalianPostalCodeValidator implements ConstraintValidator<ItalianPostalCode, CounterpartyDTO> {

    public static final String INVALID = "Il CAP italiano ha 5 cifre (es. 70043)";

    private static final Pattern FIVE_DIGITS = Pattern.compile("^\\d{5}$");
    // "IT" in any case, without a case mapping (FindSecBugs IMPROPER_UNICODE).
    private static final Pattern ITALY = Pattern.compile("^[Ii][Tt]$");

    /** True when the CAP is missing, the address is abroad, or it has five digits. */
    public static boolean isValid(String postalCode, String country) {
        if (postalCode == null || postalCode.isBlank()) {
            return true;
        }
        boolean italian = country == null || country.isBlank() || ITALY.matcher(country.trim()).matches();
        return !italian || FIVE_DIGITS.matcher(postalCode.trim()).matches();
    }

    @Override
    public boolean isValid(CounterpartyDTO dto, ConstraintValidatorContext context) {
        if (dto == null || isValid(dto.postalCode(), dto.country())) {
            return true;
        }
        if (context != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(INVALID).addPropertyNode("postalCode").addConstraintViolation();
        }
        return false;
    }
}
