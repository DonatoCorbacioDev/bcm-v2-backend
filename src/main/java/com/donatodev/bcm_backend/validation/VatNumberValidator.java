package com.donatodev.bcm_backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link ValidVatNumber}, reporting why a number is refused. */
public class VatNumberValidator implements ConstraintValidator<ValidVatNumber, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String violation = VatNumbers.violation(value);
        if (violation == null) {
            return true;
        }
        if (context != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(violation).addConstraintViolation();
        }
        return false;
    }
}
