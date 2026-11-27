package com.donatodev.bcm_backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link ValidVatNumber}. */
public class VatNumberValidator implements ConstraintValidator<ValidVatNumber, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || VatNumbers.isValid(value);
    }
}
