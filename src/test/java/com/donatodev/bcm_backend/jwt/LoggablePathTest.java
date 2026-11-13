package com.donatodev.bcm_backend.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** No secret carried in a URL reaches the logs through the request path in MDC. */
class LoggablePathTest {

    @ParameterizedTest(name = "{0}")
    @DisplayName("the calendar feed's token is masked")
    @CsvSource({
            "/api/v1/calendar/3f9c2a7b-secret-token.ics, /api/v1/calendar/***.ics",
            "/api/v1/calendar/3f9c2a7b-secret-token,     /api/v1/calendar/***",
    })
    void calendarTokenIsMasked(String path, String logged) {
        assertThat(JwtAuthenticationFilter.loggablePath(path)).isEqualTo(logged).doesNotContain("secret");
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("every other path is logged as it is")
    @CsvSource({"/api/v1/contracts/42", "/api/v1/users/me/calendar-feed", "/api/v1/auth/login"})
    void otherPathsUnchanged(String path) {
        assertThat(JwtAuthenticationFilter.loggablePath(path)).isEqualTo(path);
    }

    @Test
    @DisplayName("a request without a path stays without one")
    void nullPath() {
        assertThat(JwtAuthenticationFilter.loggablePath(null)).isNull();
    }
}
