package com.donatodev.bcm_backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenHashTest {

    @Test
    @DisplayName("SHA-256 as lowercase hex, the same as MySQL's SHA2(token, 256) that V59 used")
    void matchesTheStandardVectorAndMysqlFormat() {
        // FIPS 180-2 test vector; SELECT SHA2('abc', 256) returns exactly this.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TokenHash.sha256Hex("abc"));
    }
}
