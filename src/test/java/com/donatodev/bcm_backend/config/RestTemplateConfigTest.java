package com.donatodev.bcm_backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RestTemplateConfigTest {

    private final RestTemplateConfig config = new RestTemplateConfig();

    @Test
    @DisplayName("Applies the given connect/read timeouts to the request factory")
    void appliesConfiguredTimeouts() {
        RestTemplate restTemplate = config.restTemplate(5_000, 130_000);

        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        assertInstanceOf(SimpleClientHttpRequestFactory.class, factory);
        assertEquals(5_000, ReflectionTestUtils.getField(factory, "connectTimeout"));
        assertEquals(130_000, ReflectionTestUtils.getField(factory, "readTimeout"));
    }

    @Test
    @DisplayName("Documented default read timeout (130s) exceeds bcm-v2-ml's default OLLAMA_TIMEOUT (120s)")
    void documentedDefaultExceedsOllamaTimeout() {
        // Regression test for A2: these two numbers must be kept in this
        // relationship by hand across the two repos (application.properties'
        // ml.read-timeout-ms default here, OLLAMA_TIMEOUT in bcm-v2-ml's
        // config.py) — a slow-but-successful Ollama call must not be
        // abandoned by Java before FastAPI's own timeout has a chance to fire.
        int defaultReadTimeoutMs = 130_000;
        int ollamaDefaultTimeoutMs = 120_000;

        assertEquals(true, defaultReadTimeoutMs > ollamaDefaultTimeoutMs);
    }
}
