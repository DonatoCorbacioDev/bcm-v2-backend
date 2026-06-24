package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.donatodev.bcm_backend.config.TenantContext;

@ExtendWith(MockitoExtension.class)
class MlProxyServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MlProxyService mlProxyService;

    private static final String FASTAPI_URL = "http://localhost:8000";

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: MlProxyService")
    @SuppressWarnings("unused")
    class VerifyMlProxyService {

        @Test
        @Order(1)
        @DisplayName("getForecast forwards months and org_id from TenantContext")
        void shouldForwardForecastWithOrgId() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);

            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"historical\":[]}"));

            ResponseEntity<String> result = mlProxyService.getForecast(6);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(
                    contains("months=6"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(restTemplate).exchange(
                    contains("org_id=5"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @Order(2)
        @DisplayName("getRiskScores omits org_id when TenantContext is empty")
        void shouldNotAppendOrgIdWhenTenantContextEmpty() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);

            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlProxyService.getRiskScores();

            verify(restTemplate).exchange(
                    eq(FASTAPI_URL + "/risk-scores"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @Order(3)
        @DisplayName("Adds X-Internal-Api-Key header when configured")
        void shouldAddInternalApiKeyHeaderWhenConfigured() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            ReflectionTestUtils.setField(mlProxyService, "internalApiKey", "secret");

            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlProxyService.getRiskScores();

            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.GET),
                    org.mockito.ArgumentMatchers.argThat((HttpEntity<?> e) ->
                            "secret".equals(e.getHeaders().getFirst("X-Internal-Api-Key"))),
                    eq(String.class));
        }

        @Test
        @Order(4)
        @DisplayName("Omits X-Internal-Api-Key header when configured key is blank")
        void shouldOmitInternalApiKeyHeaderWhenBlank() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            ReflectionTestUtils.setField(mlProxyService, "internalApiKey", "  ");

            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlProxyService.getRiskScores();

            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.GET),
                    org.mockito.ArgumentMatchers.argThat((HttpEntity<?> e) ->
                            e.getHeaders().getFirst("X-Internal-Api-Key") == null),
                    eq(String.class));
        }

        @Test
        @Order(5)
        @DisplayName("Returns 503 when the ML service is unreachable")
        void shouldReturn503WhenMlUnreachable() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);

            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            ResponseEntity<String> result = mlProxyService.getRiskScores();

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        }
    }
}
