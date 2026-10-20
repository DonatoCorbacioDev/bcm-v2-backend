package com.donatodev.bcm_backend.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

class InternalApiKeyGuardTest {

    @Test
    @DisplayName("accepts the configured key")
    void shouldAcceptMatchingKey() {
        assertDoesNotThrow(() -> new InternalApiKeyGuard("s3cret").verify("s3cret"));
    }

    @Test
    @DisplayName("rejects a wrong or missing key with 403")
    void shouldRejectWrongOrMissingKey() {
        InternalApiKeyGuard guard = new InternalApiKeyGuard("s3cret");
        assertThrows(AccessDeniedException.class, () -> guard.verify("wrong"));
        assertThrows(AccessDeniedException.class, () -> guard.verify(null));
    }

    @Test
    @DisplayName("disables the endpoints with 404 when no key is configured, even for an empty header")
    void shouldBeDisabledWhenNoKeyConfigured() {
        InternalApiKeyGuard guard = new InternalApiKeyGuard("");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> guard.verify(""));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
