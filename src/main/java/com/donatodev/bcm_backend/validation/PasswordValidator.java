package com.donatodev.bcm_backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link ValidPassword}, reporting the message of the rule broken. */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private boolean optional;
    private String requiredMessage;

    @Override
    public void initialize(ValidPassword annotation) {
        this.optional = annotation.optional();
        // The annotation's message names the field when it is missing
        // ("Password amministratore obbligatoria"); the other rules have their own.
        this.requiredMessage = annotation.message();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (optional && (password == null || password.isBlank())) {
            return true;
        }
        String violation = PasswordPolicy.violation(password);
        if (violation == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        String message = PasswordPolicy.REQUIRED.equals(violation) ? requiredMessage : violation;
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
