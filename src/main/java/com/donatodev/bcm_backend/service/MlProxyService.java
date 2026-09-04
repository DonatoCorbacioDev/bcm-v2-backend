package com.donatodev.bcm_backend.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.UsersRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Authenticates and tenant-scopes calls to the ML (FastAPI) service.
 * The frontend never talks to FastAPI directly: every request is authenticated
 * and org-scoped by this proxy. Results for forecast and anomalies are cached
 * in DB (via MlCacheService) to avoid re-fitting Prophet / Isolation Forest
 * on each HTTP request.
 * <p>
 * A MANAGER caller is additionally scoped to their own assigned contracts via
 * {@code manager_id} (resolved from the authenticated principal, never client-
 * supplied) — mirroring the ADMIN/MANAGER split already enforced in
 * {@code ContractService}. An ADMIN (or an unauthenticated internal caller,
 * e.g. the nightly refresher) is scoped by {@code org_id} only.
 * <p>
 * Every call also carries an {@code X-Internal-Claims} JWT ({@link MlClaimsSigner})
 * asserting org_id/manager_id, signed with a private key FastAPI never holds —
 * see {@link MlClaimsSigner} for why this is asymmetric rather than reusing
 * {@code ml.internal-api-key} for both authentication and scope-binding.
 */
@Service
public class MlProxyService {

    @Value("${ml.fastapi.url:http://localhost:8000}")
    private String fastApiUrl;

    @Value("${ml.internal-api-key:}")
    private String internalApiKey;

    private final RestTemplate restTemplate;
    private final MlCacheService mlCacheService;
    private final MeterRegistry meterRegistry;
    private final UsersRepository usersRepository;
    private final MlClaimsSigner mlClaimsSigner;

    public MlProxyService(
            RestTemplate restTemplate,
            MlCacheService mlCacheService,
            MeterRegistry meterRegistry,
            UsersRepository usersRepository,
            MlClaimsSigner mlClaimsSigner) {
        this.restTemplate = restTemplate;
        this.mlCacheService = mlCacheService;
        this.meterRegistry = meterRegistry;
        this.usersRepository = usersRepository;
        this.mlClaimsSigner = mlClaimsSigner;
    }

    // ── Cache-aware public methods (called from HTTP requests) ──────────────

    public ResponseEntity<String> getForecast(int months) {
        Long orgId = TenantContext.get();
        Long managerId = resolveManagerId();
        String key = "FORECAST_" + months + cacheKeySuffix(managerId);
        Optional<String> cached = mlCacheService.get(orgId, key);
        if (cached.isPresent()) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(cached.get());
        }
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/forecast").queryParam("months", months);
        ResponseEntity<String> response = callMl(uriBuilder, orgId, managerId, "forecast");
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            mlCacheService.put(orgId, key, response.getBody());
        }
        return response;
    }

    public ResponseEntity<String> getAgentInsights(int months) {
        Long orgId = TenantContext.get();
        Long managerId = resolveManagerId();
        String key = "AGENT_INSIGHTS_" + months + cacheKeySuffix(managerId);
        Optional<String> cached = mlCacheService.get(orgId, key);
        if (cached.isPresent()) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(cached.get());
        }
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/agent/insights").queryParam("months", months);
        ResponseEntity<String> response = callMl(uriBuilder, orgId, managerId, "agent-insights");
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            mlCacheService.put(orgId, key, response.getBody());
        }
        return response;
    }

    public ResponseEntity<String> getAnomalies() {
        Long orgId = TenantContext.get();
        Long managerId = resolveManagerId();
        String key = "ANOMALIES" + cacheKeySuffix(managerId);
        Optional<String> cached = mlCacheService.get(orgId, key);
        if (cached.isPresent()) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(cached.get());
        }
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/anomalies");
        ResponseEntity<String> response = callMl(uriBuilder, orgId, managerId, "anomalies");
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            mlCacheService.put(orgId, key, response.getBody());
        }
        return response;
    }

    public ResponseEntity<String> getRiskScores() {
        Long orgId = TenantContext.get();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/risk-scores");
        return callMl(uriBuilder, orgId, resolveManagerId(), "risk-scores");
    }

    public ResponseEntity<String> analyzeClauseRisk(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            headers.set("X-Internal-Api-Key", internalApiKey);
        }
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("text", text), headers);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    fastApiUrl + "/clause-risk-analysis", HttpMethod.POST, entity, String.class);
            recordCallTiming(sample, "clause-risk-analysis", "success");
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (RestClientException e) {
            recordCallTiming(sample, "clause-risk-analysis", "error");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    public ResponseEntity<String> askAgent(String question) {
        Long orgId = TenantContext.get();
        Long managerId = resolveManagerId();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/agent/ask");
        if (orgId != null) {
            uriBuilder.queryParam("org_id", orgId);
        }
        if (managerId != null) {
            uriBuilder.queryParam("manager_id", managerId);
        }
        HttpHeaders headers = buildInternalHeaders(orgId, managerId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("question", question), headers);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uriBuilder.toUriString(), HttpMethod.POST, entity, String.class);
            recordCallTiming(sample, "agent-ask", "success");
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (RestClientException e) {
            recordCallTiming(sample, "agent-ask", "error");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    // ── Raw methods for the nightly refresher (bypass cache) ────────────────

    public ResponseEntity<String> fetchForecastRaw(int months, Long orgId) {
        return callMl(
                UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/forecast").queryParam("months", months), orgId, null, "forecast");
    }

    public ResponseEntity<String> fetchAnomaliesRaw(Long orgId) {
        return callMl(UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/anomalies"), orgId, null, "anomalies");
    }

    public ResponseEntity<String> fetchRiskScoresRaw(Long orgId) {
        return callMl(UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/risk-scores"), orgId, null, "risk-scores");
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Resolves the current authenticated user's manager scope: {@code null}
     * for an ADMIN (or when there is no authenticated user, e.g. an internal
     * caller), otherwise the id of the manager they are assigned to. This is
     * looked up from the security principal on every call — it is never
     * accepted as a caller-supplied parameter, so a request can't widen its
     * own scope by claiming a different manager.
     */
    private Long resolveManagerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        String username = principal instanceof UserDetails userDetails ? userDetails.getUsername() : String.valueOf(principal);

        return usersRepository.findByUsername(username)
                .filter(user -> !"ADMIN".equals(user.getRole().getRole()) && user.getManager() != null)
                .map(Users::getManager)
                .map(Managers::getId)
                .orElse(null);
    }

    private String cacheKeySuffix(Long managerId) {
        return managerId != null ? "_MGR" + managerId : "";
    }

    /**
     * Builds the headers every internal call to the ML service carries: the
     * shared-secret gate ({@code X-Internal-Api-Key}, "is this caller allowed
     * to reach the ML service at all") and, when a signing key is configured,
     * the asymmetrically-signed claims token ({@code X-Internal-Claims},
     * "which org/manager this specific request is for" — see
     * {@link MlClaimsSigner}). The two are independent and both optional in
     * local dev (empty key / no signing key configured).
     */
    private HttpHeaders buildInternalHeaders(Long orgId, Long managerId) {
        HttpHeaders headers = new HttpHeaders();
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            headers.set("X-Internal-Api-Key", internalApiKey);
        }
        String claimsToken = mlClaimsSigner.sign(orgId, managerId);
        if (claimsToken != null) {
            headers.set("X-Internal-Claims", claimsToken);
        }
        return headers;
    }

    private ResponseEntity<String> callMl(UriComponentsBuilder uriBuilder, Long orgId, Long managerId, String endpoint) {
        if (orgId != null) {
            uriBuilder.queryParam("org_id", orgId);
        }
        if (managerId != null) {
            uriBuilder.queryParam("manager_id", managerId);
        }
        HttpEntity<Void> entity = new HttpEntity<>(buildInternalHeaders(orgId, managerId));
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uriBuilder.toUriString(), HttpMethod.GET, entity, String.class);
            recordCallTiming(sample, endpoint, "success");
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (RestClientException e) {
            recordCallTiming(sample, endpoint, "error");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private void recordCallTiming(Timer.Sample sample, String endpoint, String outcome) {
        sample.stop(Timer.builder("bcm.ml.call")
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .register(meterRegistry));
    }
}
