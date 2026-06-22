package com.donatodev.bcm_backend.service;

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
 * Authenticates and tenant-scopes calls to the ML (FastAPI) service on behalf
 * of the frontend. The frontend never talks to FastAPI directly: it must go
 * through the backend, which injects the authenticated user's organization
 * ID and the shared internal API key before forwarding the request.
 */
@Service
public class MlProxyService {

    @Value("${ml.fastapi.url:http://localhost:8000}")
    private String fastApiUrl;

    @Value("${ml.internal-api-key:}")
    private String internalApiKey;

    private final RestTemplate restTemplate;

    public MlProxyService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<String> getForecast(int months) {
        return callMl(UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/forecast").queryParam("months", months));
    }

    public ResponseEntity<String> getRiskScores() {
        return callMl(UriComponentsBuilder.fromHttpUrl(fastApiUrl + "/risk-scores"));
    }

    private ResponseEntity<String> callMl(UriComponentsBuilder uriBuilder) {
        Long orgId = TenantContext.get();
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
