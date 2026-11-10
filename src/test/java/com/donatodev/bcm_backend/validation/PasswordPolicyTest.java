package com.donatodev.bcm_backend.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@DisplayName("Unit Test: the password rule")
class PasswordPolicyTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    record Required(@ValidPassword(message = "Password amministratore obbligatoria") String password) {}

    record Optional(@ValidPassword(optional = true) String password) {}

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Set<String> messages(Object bean) {
        return validator.validate(bean).stream().map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("8 characters is the shortest password accepted, 7 is refused")
    void minimumLength() {
        assertEquals(PasswordPolicy.TOO_SHORT, PasswordPolicy.violation("abcdefg"));
        assertNull(PasswordPolicy.violation("abcdefgh"));
    }

    @Test
    @DisplayName("72 bytes is the longest accepted, 73 is refused: past that BCrypt won't hash it")
    void maximumBytes() {
        assertNull(PasswordPolicy.violation("x".repeat(72)));
        assertEquals(PasswordPolicy.TOO_LONG, PasswordPolicy.violation("x".repeat(73)));
    }

    @Test
    @DisplayName("the limit counts bytes: 37 accented letters are 74 bytes and too long")
    void accentedLettersCountTwoBytes() {
        assertEquals(PasswordPolicy.TOO_LONG, PasswordPolicy.violation("è".repeat(37)));
        assertNull(PasswordPolicy.violation("è".repeat(36)));
    }

    @Test
    @DisplayName("the minimum counts characters: 8 emoji are a valid password, 32 bytes")
    void minimumCountsCharactersNotBytes() {
        assertNull(PasswordPolicy.violation("😀".repeat(8)));
        assertEquals(PasswordPolicy.TOO_SHORT, PasswordPolicy.violation("😀".repeat(7)));
    }

    @Test
    @DisplayName("missing or blank is refused, and require() throws the Italian message")
    void missingPassword() {
        assertEquals(PasswordPolicy.REQUIRED, PasswordPolicy.violation(null));
        assertEquals(PasswordPolicy.REQUIRED, PasswordPolicy.violation("   "));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PasswordPolicy.require("short"));
        assertEquals(PasswordPolicy.TOO_SHORT, ex.getMessage());
    }

    @Test
    @DisplayName("@ValidPassword names the field when missing and reports the broken rule otherwise")
    void validatorMessages() {
        assertThat(messages(new Required(null))).containsExactly("Password amministratore obbligatoria");
        assertThat(messages(new Required("short"))).containsExactly(PasswordPolicy.TOO_SHORT);
        assertThat(messages(new Required("x".repeat(73)))).containsExactly(PasswordPolicy.TOO_LONG);
        assertThat(messages(new Required("long-enough"))).isEmpty();
    }

    @Test
    @DisplayName("@ValidPassword(optional) accepts no password but still checks one that is given")
    void optionalPassword() {
        assertThat(messages(new Optional(null))).isEmpty();
        assertThat(messages(new Optional(""))).isEmpty();
        assertThat(messages(new Optional("short"))).containsExactly(PasswordPolicy.TOO_SHORT);
    }
}
