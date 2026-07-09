package com.donatodev.bcm_backend.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IbanValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "DE89370400440532013000",
            "IT60X0542811101000000123456",
            "FR1420041010050500013M02606",
            "GB29NWBK60161331926819",
            "de89 3704 0044 0532 0130 00"
    })
    @DisplayName("isValid: accepts real-world IBANs, with or without spaces/lowercase")
    void shouldAcceptValidIbans(String iban) {
        assertTrue(IbanValidator.isValid(iban));
    }

    @Test
    @DisplayName("isValid: rejects an IBAN with a bad checksum")
    void shouldRejectBadChecksum() {
        assertFalse(IbanValidator.isValid("IT00X0000000000000000000000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "NOTANIBAN",
            "DE8937040044053201300", // structurally valid length but wrong checksum (truncated real IBAN)
            "D389370400440532013000", // digit where country letter expected
            "DEXX370400440532013000" // non-digit check digits
    })
    @DisplayName("isValid: rejects malformed IBANs")
    void shouldRejectMalformedIbans(String iban) {
        assertFalse(IbanValidator.isValid(iban));
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("isValid: rejects null")
    void shouldRejectNull(String iban) {
        assertFalse(IbanValidator.isValid(iban));
    }
}
