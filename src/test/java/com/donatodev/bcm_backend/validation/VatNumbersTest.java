package com.donatodev.bcm_backend.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Unit Test: the shape of a VAT number")
class VatNumbersTest {

    private final VatNumberValidator validator = new VatNumberValidator();

    @ParameterizedTest
    @ValueSource(strings = {
        "IT01234567897", // the example in the message
        "01234567897", // without the country code
        "it 0123 4567 897", // spaces and lower case
        "IT07824150960", // the demo's Nuvola Cloud Servizi
        "DE123456789", // foreign: only the shape is checked
        "FRXX123456789",
    })
    @DisplayName("accepts a well-formed Italian or foreign VAT number")
    void accepts(String vat) {
        assertThat(VatNumbers.isValid(vat)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "IT00000000", // too short: the one found in the interface review
        "IT01234567890", // wrong check digit
        "IT00000000000", // all zeros pass the check digit, still not a number
        "IT012345678971", // too long
        "0123456789A",
        "D",
        "1234",
    })
    @DisplayName("refuses a malformed one")
    void refuses(String vat) {
        assertThat(VatNumbers.isValid(vat)).isFalse();
    }

    @Test
    @DisplayName("the field stays optional: missing or blank is valid")
    void optional() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("  ", null)).isTrue();
        assertThat(validator.isValid("IT00000000", null)).isFalse();
    }

    @Test
    @DisplayName("normalizes spaces and case")
    void normalizes() {
        assertThat(VatNumbers.normalize(" it 0123 ")).isEqualTo("IT0123");
        assertThat(VatNumbers.normalize(null)).isNull();
    }
}
