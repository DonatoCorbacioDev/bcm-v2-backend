package com.donatodev.bcm_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.donatodev.bcm_backend.config.LegalTerms;
import com.donatodev.bcm_backend.entity.PendingRegistration;
import com.donatodev.bcm_backend.repository.PendingRegistrationRepository;

/**
 * Over a real Tomcat, not MockMvc: the client IP comes from Tomcat's
 * RemoteIpValve ({@code server.forward-headers-strategy=native}), which
 * MockMvc never runs. In production every request reaches the backend from
 * Caddy, so without the valve the stored IP — and the rate limiter's bucket —
 * was the proxy's for every visitor. Same annotations as
 * {@link ResponseStatusOverHttpTest}, so the two share one Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {"internal.admin-api-key=", "stripe.webhook-secret="})
class ClientIpOverHttpTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PendingRegistrationRepository pendingRegistrationRepository;

    @AfterEach
    void cleanup() {
        pendingRegistrationRepository.deleteAll();
    }

    @Test
    @DisplayName("a registration behind the proxy records the client IP from X-Forwarded-For, not the proxy's")
    void recordsForwardedClientIp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", "203.0.113.50");
        String body = """
                {
                  "organizationName": "Proxy Corp",
                  "adminUsername":    "proxyadmin",
                  "adminPassword":    "password123",
                  "adminEmail":       "admin@proxycorp.com",
                  "adminFirstName":   "Proxy",
                  "adminLastName":    "Admin",
                  "termsVersion":     "%s",
                  "termsAccepted":    true,
                  "professionalUse":  true,
                  "onerousClausesApproved": true
                }
                """.formatted(LegalTerms.CURRENT_VERSION);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/organizations/register", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        PendingRegistration pending = pendingRegistrationRepository.findAll().stream()
                .filter(p -> "admin@proxycorp.com".equals(p.getAdminEmail()))
                .findFirst().orElseThrow();
        assertThat(pending.getRequestIp()).isEqualTo("203.0.113.50");
    }
}
