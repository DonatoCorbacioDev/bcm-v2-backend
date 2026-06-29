package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

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

    @Mock
    private MlCacheService mlCacheService;

    @InjectMocks
    private MlProxyService mlProxyService;

    private static final String FASTAPI_URL = "http://localhost:8000";

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("getForecast()")
    @SuppressWarnings("unused")
    class GetForecast {

        @Test
        @Order(1)
        @DisplayName("Forwards months and org_id to FastAPI on cache miss")
        void shouldForwardForecastWithOrgId() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            when(mlCacheService.get(5L, "FORECAST_6")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"historical\":[]}"));

            ResponseEntity<String> result = mlProxyService.getForecast(6);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(contains("months=6"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(restTemplate).exchange(contains("org_id=5"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @Order(2)
        @DisplayName("Returns cached value without calling FastAPI on cache hit")
        void shouldReturnCachedValueOnHit() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(1L);
            when(mlCacheService.get(1L, "FORECAST_3")).thenReturn(Optional.of("{\"historical\":[{\"month\":\"2025-01\"}]}"));

            ResponseEntity<String> result = mlProxyService.getForecast(3);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("getAnomalies()")
    @SuppressWarnings("unused")
    class GetAnomalies {

        @Test
        @Order(3)
        @DisplayName("Calls FastAPI /anomalies with org_id on cache miss")
        void shouldCallFastApiOnCacheMiss() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(2L);
            when(mlCacheService.get(2L, "ANOMALIES")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("[]"));

            ResponseEntity<String> result = mlProxyService.getAnomalies();

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(contains("/anomalies"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(restTemplate).exchange(contains("org_id=2"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @Order(4)
        @DisplayName("Returns cached anomalies without calling FastAPI")
        void shouldReturnCachedAnomalies() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(3L);
            when(mlCacheService.get(3L, "ANOMALIES")).thenReturn(Optional.of("[{\"severity\":\"HIGH\"}]"));

            ResponseEntity<String> result = mlProxyService.getAnomalies();

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("getRiskScores()")
    @SuppressWarnings("unused")
    class GetRiskScores {

        @Test
        @Order(5)
        @DisplayName("Omits org_id when TenantContext is empty")
        void shouldNotAppendOrgIdWhenTenantContextEmpty() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlProxyService.getRiskScores();

            verify(restTemplate).exchange(
                    eq(FASTAPI_URL + "/risk-scores"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Raw fetch methods (for refresher)")
    @SuppressWarnings("unused")
    class RawFetch {

        @Test
        @Order(6)
        @DisplayName("fetchForecastRaw bypasses cache and calls FastAPI directly")
        void fetchForecastRawBypassesCache() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{}"));

            mlProxyService.fetchForecastRaw(3, 10L);

            verify(mlCacheService, never()).get(any(), anyString());
            verify(restTemplate).exchange(contains("months=3"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(restTemplate).exchange(contains("org_id=10"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @Order(7)
        @DisplayName("fetchAnomaliesRaw bypasses cache and calls FastAPI directly")
        void fetchAnomaliesRawBypassesCache() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlProxyService.fetchAnomaliesRaw(7L);

            verify(mlCacheService, never()).get(any(), anyString());
            verify(restTemplate).exchange(contains("/anomalies"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(restTemplate).exchange(contains("org_id=7"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Error handling and headers")
    @SuppressWarnings("unused")
    class ErrorHandling {

        @Test
        @Order(8)
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
        @Order(9)
        @DisplayName("Omits X-Internal-Api-Key header when key is blank")
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
        @Order(10)
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
