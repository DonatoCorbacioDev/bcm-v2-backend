package com.donatodev.bcm_backend.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * An optional VAT number that, when given, has the shape of one
 * ({@link VatNumbers}): a missing or blank value is valid.
 */
@Documented
@Constraint(validatedBy = VatNumberValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidVatNumber {

    String message() default VatNumbers.INVALID;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
