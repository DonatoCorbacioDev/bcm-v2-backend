/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A User-Agent read as the kind of device a person would recognise, without versions. */
class DeviceSummaryTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36|Chrome su Windows",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0|Edge su Windows",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15|Safari su macOS",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1|Safari su iPhone",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36|Chrome su Android",
            "Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0|Firefox su Linux",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 OPR/115.0.0.0|Opera su Windows"
    })
    @DisplayName("each common browser and system is named")
    void names(String userAgent, String expected) {
        assertThat(DeviceSummary.of(userAgent)).isEqualTo(expected);
    }

    @Test
    @DisplayName("an update of the browser is not a new device")
    void versionsDoNotCount() {
        assertThat(DeviceSummary.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0.0.0 Safari/537.36"))
                .isEqualTo(DeviceSummary.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0 Safari/537.36"));
    }

    @Test
    @DisplayName("no User-Agent, or one not recognised, is named generically")
    void unknown() {
        assertThat(DeviceSummary.of(null)).isEqualTo("Browser sconosciuto su sistema sconosciuto");
        assertThat(DeviceSummary.of("curl/8.5.0")).isEqualTo("Browser sconosciuto su sistema sconosciuto");
    }
}
