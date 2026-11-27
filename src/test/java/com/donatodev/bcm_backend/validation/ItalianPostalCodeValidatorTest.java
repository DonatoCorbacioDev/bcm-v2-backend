package com.donatodev.bcm_backend.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Unit Test: the Italian CAP")
class ItalianPostalCodeValidatorTest {

    @ParameterizedTest(name = "CAP \"{0}\", country \"{1}\" -> {2}")
    @CsvSource(value = {
        "70043, IT, true",
        "70043, NULL, true",
        "' 70043 ', it, true",
        "NULL, IT, true",
        "'', IT, true",
        "7004, IT, false",
        "700433, NULL, false",
        "70A43, IT, false",
        "SW1A 1AA, GB, true", // abroad: its own format
        "10115, DE, true",
    }, nullValues = "NULL")
    void checks(String postalCode, String country, boolean valid) {
        assertThat(ItalianPostalCodeValidator.isValid(postalCode, country)).isEqualTo(valid);
    }
}
