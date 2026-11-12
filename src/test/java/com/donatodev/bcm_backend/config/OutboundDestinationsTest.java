package com.donatodev.bcm_backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** OWASP API7: the backend only ever calls the hosts on its allowlist. */
class OutboundDestinationsTest {

    private static final String DEFAULT_HOSTS = "ml,localhost,127.0.0.1,host.docker.internal";

    private static OutboundDestinations with(String hosts) {
        return new OutboundDestinations(hosts, "http://ml:8000", "http://host.docker.internal:11434");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://ml:8000", "http://localhost:8000/", "https://127.0.0.1:8443",
            "http://host.docker.internal:11434", "HTTP://ML:8000"})
    @DisplayName("the services next to the backend are allowed")
    void allowedDestinations(String url) {
        assertThatCode(() -> with(DEFAULT_HOSTS).requireAllowed("ML_FASTAPI_URL", url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://169.254.169.254/latest/meta-data", "http://evil.example.com",
            "file:///etc/passwd", "gopher://ml:70", "ftp://ml/x", "http://user:pass@ml:8000",
            "http://ml.evil.example.com", "//ml:8000", "", "http://[::1", "ml:8000"})
    @DisplayName("anything else stops the application at startup")
    void refusedDestinations(String url) {
        OutboundDestinations destinations = with(DEFAULT_HOSTS);
        assertThatThrownBy(() -> destinations.requireAllowed("ML_FASTAPI_URL", url))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ML_FASTAPI_URL");
    }

    @Test
    @DisplayName("checked at construction: a bad ML or Ollama URL never gets a running application")
    void checkedAtStartup() {
        assertThatThrownBy(() -> new OutboundDestinations(DEFAULT_HOSTS, "http://10.0.0.5:8000",
                "http://localhost:11434")).hasMessageContaining("ML_FASTAPI_URL");
        assertThatThrownBy(() -> new OutboundDestinations(DEFAULT_HOSTS, "http://ml:8000",
                "http://metadata.google.internal")).hasMessageContaining("OLLAMA_URL");
        assertThatThrownBy(() -> new OutboundDestinations(DEFAULT_HOSTS, null, "http://localhost:11434"))
                .hasMessageContaining("ML_FASTAPI_URL");
    }

    @Test
    @DisplayName("the allowlist comes from configuration, spaces and case aside")
    void allowlistFromConfiguration() {
        OutboundDestinations destinations = new OutboundDestinations(" ML-Internal , ollama ,",
                "http://ml-internal:8000", "http://ollama:11434");

        assertThatThrownBy(() -> destinations.requireAllowed("ML_FASTAPI_URL", "http://ml:8000"))
                .isInstanceOf(IllegalStateException.class);
    }
}
