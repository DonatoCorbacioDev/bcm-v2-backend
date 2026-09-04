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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.UsersRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class MlProxyServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private MlCacheService mlCacheService;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private MlClaimsSigner mlClaimsSigner;

    private SimpleMeterRegistry meterRegistry;
    private MlProxyService mlProxyService;

    private static final String FASTAPI_URL = "http://localhost:8000";

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        mlProxyService = new MlProxyService(restTemplate, mlCacheService, meterRegistry, usersRepository, mlClaimsSigner);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    /** Authenticates as a MANAGER assigned to the given managerId, so resolveManagerId() picks it up. */
    private void authenticateAsManager(Long managerId) {
        Managers manager = Managers.builder().id(managerId).build();
        Roles managerRole = new Roles();
        managerRole.setRole("MANAGER");
        Users user = Users.builder().username("manager1").role(managerRole).manager(manager).build();
        org.mockito.Mockito.lenient().when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager1", null, java.util.List.of()));
    }

    /** Authenticates as an ADMIN, so resolveManagerId() must return null (org-wide scope). */
    private void authenticateAsAdmin() {
        Roles adminRole = new Roles();
        adminRole.setRole("ADMIN");
        Users user = Users.builder().username("admin1").role(adminRole).build();
        org.mockito.Mockito.lenient().when(usersRepository.findByUsername("admin1")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin1", null, java.util.List.of()));
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
            assertEquals(1, meterRegistry.get("bcm.ml.call").tag("endpoint", "forecast").tag("outcome", "success")
                    .timer().count());
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

        @Test
        @Order(3)
        @DisplayName("Does not cache a non-2xx response from FastAPI")
        void shouldNotCacheOnNon2xxResponse() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            when(mlCacheService.get(5L, "FORECAST_6")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));

            ResponseEntity<String> result = mlProxyService.getForecast(6);

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
            verify(mlCacheService, never()).put(any(), any(), any());
            assertEquals(1, meterRegistry.get("bcm.ml.call").tag("endpoint", "forecast").tag("outcome", "error")
                    .timer().count());
        }

        @Test
        @Order(4)
        @DisplayName("Does not cache a 2xx response with a null body")
        void shouldNotCacheOnNullBody() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            when(mlCacheService.get(5L, "FORECAST_6")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.status(HttpStatus.OK).body(null));

            ResponseEntity<String> result = mlProxyService.getForecast(6);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(mlCacheService, never()).put(any(), any(), any());
        }

        @Test
        @Order(5)
        @DisplayName("A MANAGER's request adds manager_id and uses a manager-specific cache key")
        void shouldForwardManagerIdAndUseManagerScopedCacheKey() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            authenticateAsManager(42L);
            when(mlCacheService.get(5L, "FORECAST_6_MGR42")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"historical\":[]}"));

            ResponseEntity<String> result = mlProxyService.getForecast(6);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(contains("manager_id=42"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(mlCacheService).put(5L, "FORECAST_6_MGR42", "{\"historical\":[]}");
        }

        @Test
        @Order(6)
        @DisplayName("An ADMIN's request never adds manager_id, uses the plain org cache key")
        void shouldNotForwardManagerIdForAdmin() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            authenticateAsAdmin();
            when(mlCacheService.get(5L, "FORECAST_6")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"historical\":[]}"));

            mlProxyService.getForecast(6);

            verify(restTemplate, never()).exchange(contains("manager_id"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(mlCacheService).put(5L, "FORECAST_6", "{\"historical\":[]}");
        }

        @Test
        @Order(7)
        @DisplayName("Attaches X-Internal-Claims when claims signing is configured")
        void shouldAttachSignedClaimsWhenConfigured() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            when(mlCacheService.get(5L, "FORECAST_6")).thenReturn(Optional.empty());
            when(mlClaimsSigner.sign(5L, null)).thenReturn("signed-token");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"historical\":[]}"));

            mlProxyService.getForecast(6);

            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.GET),
                    org.mockito.ArgumentMatchers.argThat((HttpEntity<?> e) ->
                            "signed-token".equals(e.getHeaders().getFirst("X-Internal-Claims"))),
                    eq(String.class));
        }

        @Test
        @Order(8)
        @DisplayName("Omits X-Internal-Claims when claims signing is not configured")
        void shouldOmitClaimsHeaderWhenNotConfigured() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            when(mlCacheService.get(5L, "FORECAST_6")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"historical\":[]}"));

            mlProxyService.getForecast(6);

            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.GET),
                    org.mockito.ArgumentMatchers.argThat((HttpEntity<?> e) ->
                            e.getHeaders().getFirst("X-Internal-Claims") == null),
                    eq(String.class));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("getAgentInsights()")
    @SuppressWarnings("unused")
    class GetAgentInsights {

        @Test
        @Order(1)
        @DisplayName("Forwards months and org_id to FastAPI on cache miss")
        void shouldForwardAgentInsightsWithOrgId() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            when(mlCacheService.get(5L, "AGENT_INSIGHTS_6")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"report\":\"...\"}"));

            ResponseEntity<String> result = mlProxyService.getAgentInsights(6);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(contains("/agent/insights"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(restTemplate).exchange(contains("months=6"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(restTemplate).exchange(contains("org_id=5"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(mlCacheService).put(5L, "AGENT_INSIGHTS_6", "{\"report\":\"...\"}");
        }

        @Test
        @Order(2)
        @DisplayName("Returns cached value without calling FastAPI on cache hit")
        void shouldReturnCachedValueOnHit() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(1L);
            when(mlCacheService.get(1L, "AGENT_INSIGHTS_3")).thenReturn(Optional.of("{\"report\":\"cached\"}"));

            ResponseEntity<String> result = mlProxyService.getAgentInsights(3);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("{\"report\":\"cached\"}", result.getBody());
            verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        }

        @Test
        @Order(3)
        @DisplayName("Does not cache a non-2xx response from FastAPI")
        void shouldNotCacheOnNon2xxResponse() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            when(mlCacheService.get(5L, "AGENT_INSIGHTS_3")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            ResponseEntity<String> result = mlProxyService.getAgentInsights(3);

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
            verify(mlCacheService, never()).put(any(), any(), any());
        }

        @Test
        @Order(4)
        @DisplayName("Does not cache a 2xx response with a null body")
        void shouldNotCacheOnNullBody() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            when(mlCacheService.get(5L, "AGENT_INSIGHTS_3")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.status(HttpStatus.OK).body(null));

            ResponseEntity<String> result = mlProxyService.getAgentInsights(3);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(mlCacheService, never()).put(any(), any(), any());
        }

        @Test
        @Order(5)
        @DisplayName("A MANAGER's request adds manager_id and uses a manager-specific cache key")
        void shouldForwardManagerIdAndUseManagerScopedCacheKey() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            authenticateAsManager(42L);
            when(mlCacheService.get(5L, "AGENT_INSIGHTS_3_MGR42")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"report\":\"...\"}"));

            ResponseEntity<String> result = mlProxyService.getAgentInsights(3);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(contains("manager_id=42"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(mlCacheService).put(5L, "AGENT_INSIGHTS_3_MGR42", "{\"report\":\"...\"}");
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

        @Test
        @Order(5)
        @DisplayName("Does not cache a non-2xx response from FastAPI")
        void shouldNotCacheOnNon2xxResponse() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(2L);
            when(mlCacheService.get(2L, "ANOMALIES")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));

            ResponseEntity<String> result = mlProxyService.getAnomalies();

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
            verify(mlCacheService, never()).put(any(), any(), any());
        }

        @Test
        @Order(6)
        @DisplayName("Does not cache a 2xx response with a null body")
        void shouldNotCacheOnNullBody() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(2L);
            when(mlCacheService.get(2L, "ANOMALIES")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.status(HttpStatus.OK).body(null));

            ResponseEntity<String> result = mlProxyService.getAnomalies();

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(mlCacheService, never()).put(any(), any(), any());
        }

        @Test
        @Order(7)
        @DisplayName("A MANAGER's request adds manager_id and uses a manager-specific cache key")
        void shouldForwardManagerIdAndUseManagerScopedCacheKey() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(2L);
            authenticateAsManager(42L);
            when(mlCacheService.get(2L, "ANOMALIES_MGR42")).thenReturn(Optional.empty());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("[]"));

            ResponseEntity<String> result = mlProxyService.getAnomalies();

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(contains("manager_id=42"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(mlCacheService).put(2L, "ANOMALIES_MGR42", "[]");
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

        @Test
        @Order(6)
        @DisplayName("A MANAGER's request adds manager_id")
        void shouldForwardManagerId() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(5L);
            authenticateAsManager(42L);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlProxyService.getRiskScores();

            verify(restTemplate).exchange(contains("manager_id=42"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
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

        @Test
        @Order(8)
        @DisplayName("fetchRiskScoresRaw bypasses cache, attaches org_id and the internal API key")
        void fetchRiskScoresRawBypassesCache() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            ReflectionTestUtils.setField(mlProxyService, "internalApiKey", "secret");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlProxyService.fetchRiskScoresRaw(7L);

            verify(mlCacheService, never()).get(any(), anyString());
            verify(restTemplate).exchange(contains("/risk-scores"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(restTemplate).exchange(contains("org_id=7"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.GET),
                    org.mockito.ArgumentMatchers.argThat((HttpEntity<?> e) ->
                            "secret".equals(e.getHeaders().getFirst("X-Internal-Api-Key"))),
                    eq(String.class));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("analyzeClauseRisk()")
    @SuppressWarnings("unused")
    class AnalyzeClauseRisk {

        @Test
        @Order(1)
        @DisplayName("Posts text to /clause-risk-analysis and returns the response body")
        void shouldPostTextAndReturnResponse() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            when(restTemplate.exchange(eq(FASTAPI_URL + "/clause-risk-analysis"), eq(HttpMethod.POST),
                    any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"clauses\":[]}"));

            ResponseEntity<String> result = mlProxyService.analyzeClauseRisk("some contract text");

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("{\"clauses\":[]}", result.getBody());
            verify(restTemplate).exchange(eq(FASTAPI_URL + "/clause-risk-analysis"), eq(HttpMethod.POST),
                    org.mockito.ArgumentMatchers.argThat((HttpEntity<?> e) ->
                            "some contract text".equals(((java.util.Map<?, ?>) e.getBody()).get("text"))),
                    eq(String.class));
        }

        @Test
        @Order(2)
        @DisplayName("Adds X-Internal-Api-Key header when configured")
        void shouldAddInternalApiKeyHeader() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            ReflectionTestUtils.setField(mlProxyService, "internalApiKey", "secret");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"clauses\":[]}"));

            mlProxyService.analyzeClauseRisk("some text");

            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.POST),
                    org.mockito.ArgumentMatchers.argThat((HttpEntity<?> e) ->
                            "secret".equals(e.getHeaders().getFirst("X-Internal-Api-Key"))),
                    eq(String.class));
        }

        @Test
        @Order(21)
        @DisplayName("Omits X-Internal-Api-Key header when key is blank")
        void shouldOmitInternalApiKeyHeaderWhenBlank() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            ReflectionTestUtils.setField(mlProxyService, "internalApiKey", "  ");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"clauses\":[]}"));

            mlProxyService.analyzeClauseRisk("some text");

            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.POST),
                    org.mockito.ArgumentMatchers.argThat((HttpEntity<?> e) ->
                            e.getHeaders().getFirst("X-Internal-Api-Key") == null),
                    eq(String.class));
        }

        @Test
        @Order(3)
        @DisplayName("Returns 503 when the ML service is unreachable")
        void shouldReturn503WhenMlUnreachable() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            ResponseEntity<String> result = mlProxyService.analyzeClauseRisk("some text");

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("askAgent()")
    @SuppressWarnings("unused")
    class AskAgent {

        @Test
        @Order(1)
        @DisplayName("Posts the question and org_id to /agent/ask and returns the response body")
        void shouldPostQuestionWithOrgId() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(4L);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"answer\":\"...\"}"));

            ResponseEntity<String> result = mlProxyService.askAgent("Which contracts expire soon?");

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(contains("org_id=4"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.POST),
                    org.mockito.ArgumentMatchers.argThat((HttpEntity<?> e) ->
                            "Which contracts expire soon?".equals(((java.util.Map<?, ?>) e.getBody()).get("question"))),
                    eq(String.class));
        }

        @Test
        @Order(2)
        @DisplayName("Omits org_id when TenantContext is empty")
        void shouldOmitOrgIdWhenTenantContextEmpty() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{}"));

            mlProxyService.askAgent("Any question");

            verify(restTemplate).exchange(
                    eq(FASTAPI_URL + "/agent/ask"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @Order(3)
        @DisplayName("Returns 503 when the ML service is unreachable")
        void shouldReturn503WhenMlUnreachable() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            ResponseEntity<String> result = mlProxyService.askAgent("Any question");

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        }

        @Test
        @Order(4)
        @DisplayName("A MANAGER's request adds manager_id alongside org_id")
        void shouldForwardManagerId() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(4L);
            authenticateAsManager(42L);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"answer\":\"...\"}"));

            mlProxyService.askAgent("Which contracts expire soon?");

            verify(restTemplate).exchange(contains("manager_id=42"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @Order(5)
        @DisplayName("Attaches X-Internal-Claims when claims signing is configured")
        void shouldAttachSignedClaims() {
            ReflectionTestUtils.setField(mlProxyService, "fastApiUrl", FASTAPI_URL);
            TenantContext.set(4L);
            when(mlClaimsSigner.sign(4L, null)).thenReturn("signed-token");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"answer\":\"...\"}"));

            mlProxyService.askAgent("Any question");

            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.POST),
                    org.mockito.ArgumentMatchers.argThat((HttpEntity<?> e) ->
                            "signed-token".equals(e.getHeaders().getFirst("X-Internal-Claims"))),
                    eq(String.class));
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
