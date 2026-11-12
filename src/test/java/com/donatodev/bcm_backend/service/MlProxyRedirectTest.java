package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.donatodev.bcm_backend.repository.UsersRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * OWASP API7: what the ML service answers reaches the user only when it is a
 * success. A redirect (not followed, see RestTemplateConfig) becomes a bare
 * 503: neither its body nor where it points leaves the backend.
 */
class MlProxyRedirectTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MlProxyService service = new MlProxyService(restTemplate, mock(MlCacheService.class), meters,
            mock(UsersRepository.class), mock(MlClaimsSigner.class), mock(TierLimitService.class));

    @Test
    @DisplayName("a 302 from the ML service is a 503 with no body and no Location")
    void redirectIsNotPassedOn() {
        ReflectionTestUtils.setField(service, "fastApiUrl", "http://ml");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", "http://169.254.169.254/latest/meta-data")
                        .body("internal details"));

        ResponseEntity<String> response = service.analyzeClauseRisk("clausola");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getLocation()).isNull();
        assertThat(meters.timer("bcm.ml.call", "endpoint", "clause-risk-analysis", "outcome", "error").count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a success passes through as JSON")
    void successPassesThrough() {
        ReflectionTestUtils.setField(service, "fastApiUrl", "http://ml");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"risk\":\"low\"}"));

        ResponseEntity<String> response = service.analyzeClauseRisk("clausola");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"risk\":\"low\"}");
    }
}
