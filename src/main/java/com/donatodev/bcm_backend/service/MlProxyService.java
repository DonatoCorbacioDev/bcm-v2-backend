package com.donatodev.bcm_backend.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.donatodev.bcm_backend.config.TenantContext;

/**
 * Authenticates and tenant-scopes calls to the ML (FastAPI) service.
 * The frontend never talks to FastAPI directly: every request is authenticated
 * and org-scoped by this proxy. Results for forecast and anomalies are cached
 * in DB (via MlCacheService) to avoid re-fitting Prophet / Isolation Forest
 * on each HTTP request.
 */
@Service
public class MlProxyService {

    @Value("${ml.fastapi.url:http://localhost:8000}")
    private String fastApiUrl;

    @Value("${ml.internal-api-key:}")
    private String internalApiKey;

    private final RestTemplate restTemplate;
    private final MlCacheService mlCacheService;

    public MlProxyService(RestTemplate restTemplate, MlCacheService mlCacheService) {
        this.restTemplate = restTemplate;
        this.mlCacheService = mlCacheService;
    }

    // ── Cache-aware public methods (called from HTTP requests) ──────────────

    public ResponseEntity<String> getForecast(int months) {
        Long orgId = TenantContext.get();
        String key = "FORECAST_" + months;
        Optional<String> cached = mlCacheService.get(orgId, key);
        if (cached.isPresent()) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(cached.get());
        }
        ResponseEntity<String> response = callMl(
                UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/forecast").queryParam("months", months), orgId);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            mlCacheService.put(orgId, key, response.getBody());
        }
        return response;
    }

    public ResponseEntity<String> getAnomalies() {
        Long orgId = TenantContext.get();
        Optional<String> cached = mlCacheService.get(orgId, "ANOMALIES");
        if (cached.isPresent()) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(cached.get());
        }
        ResponseEntity<String> response = callMl(
                UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/anomalies"), orgId);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            mlCacheService.put(orgId, "ANOMALIES", response.getBody());
        }
        return response;
    }

    public ResponseEntity<String> getRiskScores() {
        Long orgId = TenantContext.get();
        return callMl(UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/risk-scores"), orgId);
    }

    // ── Raw methods for the nightly refresher (bypass cache) ────────────────

    public ResponseEntity<String> fetchForecastRaw(int months, Long orgId) {
        return callMl(
                UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/forecast").queryParam("months", months), orgId);
    }

    public ResponseEntity<String> fetchAnomaliesRaw(Long orgId) {
        return callMl(UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/anomalies"), orgId);
    }

    // ── Internal helper ──────────────────────────────────────────────────────

    private ResponseEntity<String> callMl(UriComponentsBuilder uriBuilder, Long orgId) {
        if (orgId != null) {
            uriBuilder.queryParam("org_id", orgId);
        }
        HttpHeaders headers = new HttpHeaders();
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            headers.set("X-Internal-Api-Key", internalApiKey);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uriBuilder.toUriString(), HttpMethod.GET, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
