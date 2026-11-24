/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How the Pwned Passwords range API is asked and its answer read, without the network. */
class PwnedPasswordsClientTest {

    @Test
    @DisplayName("the SHA-1 is the API's: upper-case hex, of which only the first 5 characters are sent")
    void sha1() {
        // The published SHA-1 of "password".
        assertThat(PwnedPasswordsClient.sha1Hex("password")).isEqualTo("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8");
    }

    @Test
    @DisplayName("a suffix listed with a count is leaked; a padding line (count 0) or no line is not")
    void readsTheRange() {
        String body = "1E4C9B93F3F0682250B6CF8331B7EE68FD8:9659365\r\n"
                + "011053FD0102E94D6AE2F8B83D76FAF94F6:1\r\n"
                + "00000000000000000000000000000000000:0\r\n";
        assertThat(PwnedPasswordsClient.listsSuffix(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8")).isTrue();
        assertThat(PwnedPasswordsClient.listsSuffix(body, "00000000000000000000000000000000000")).isFalse();
        assertThat(PwnedPasswordsClient.listsSuffix(body, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF")).isFalse();
    }

    @Test
    @DisplayName("switched off, it asks nothing and says it does not know")
    void off() {
        assertThat(new PwnedPasswordsClient(false).isBreached("password")).isEmpty();
    }
}
