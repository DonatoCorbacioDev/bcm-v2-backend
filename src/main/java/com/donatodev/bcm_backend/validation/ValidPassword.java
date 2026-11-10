package com.donatodev.bcm_backend.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The one password rule, wherever a password is set (OWASP API2): at least
 * {@link PasswordPolicy#MIN_LENGTH} characters and at most
 * {@link PasswordPolicy#MAX_BYTES} bytes in UTF-8, the most BCrypt reads
 * (Spring Security refuses to hash a longer one).
 *
 * <p>With {@code optional = true} a missing or blank value is valid: it means
 * "don't change the password". The rule only applies to a password being set,
 * so an existing shorter one keeps working.
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default PasswordPolicy.REQUIRED;

    boolean optional() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
