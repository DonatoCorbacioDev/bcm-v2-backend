package com.donatodev.bcm_backend.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * On a counterparty: an Italian address (country empty or "IT") has a CAP of
 * five digits. A foreign one keeps whatever its country uses. The error is
 * reported on the postalCode field.
 */
@Documented
@Constraint(validatedBy = ItalianPostalCodeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ItalianPostalCode {

    String message() default ItalianPostalCodeValidator.INVALID;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
