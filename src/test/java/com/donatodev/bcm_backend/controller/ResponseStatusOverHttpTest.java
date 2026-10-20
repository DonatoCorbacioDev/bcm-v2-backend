package com.donatodev.bcm_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Regression test over a real HTTP server, not MockMvc: MockMvc never performs
 * the servlet container's error dispatch to /error, which is exactly where a
 * ResponseStatusException used to turn into a 401 (found 2026-10-20 against
 * the deployed app — every one of these returned 401).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {"internal.admin-api-key=", "stripe.webhook-secret="})
class ResponseStatusOverHttpTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    @DisplayName("a disabled internal endpoint answers 404, not 401")
    void disabledInternalEndpointIs404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/organizations/1/tier", HttpMethod.PUT, json("{\"tier\":\"PRO\"}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a disabled billing webhook answers 404, not 401")
    void disabledWebhookIs404() {
        ResponseEntity<String> response = restTemplate.postForEntity("/billing/webhook", json("{}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
