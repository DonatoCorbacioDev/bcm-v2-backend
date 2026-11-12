package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
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
 * The shared cap on language-model calls (OWASP API4): Ollama runs on the
 * API's own CPU, so a call beyond {@link MlProxyService#MAX_CONCURRENT_LLM_CALLS}
 * is refused at once instead of queueing until every request slows down.
 */
class MlProxyLlmGateTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MlProxyService service = new MlProxyService(restTemplate, mock(MlCacheService.class), meters,
            mock(UsersRepository.class), mock(MlClaimsSigner.class), mock(TierLimitService.class));
    private final ExecutorService pool = Executors.newFixedThreadPool(MlProxyService.MAX_CONCURRENT_LLM_CALLS);
    private final CountDownLatch modelAnswers = new CountDownLatch(1);

    @AfterEach
    void tearDown() {
        modelAnswers.countDown();
        pool.shutdownNow();
    }

    @Test
    @DisplayName("with every slot taken, the next call gets 503 at once; a freed slot serves again")
    void refusesBeyondTheCap() throws Exception {
        ReflectionTestUtils.setField(service, "fastApiUrl", "http://ml");
        CountDownLatch slotsTaken = new CountDownLatch(MlProxyService.MAX_CONCURRENT_LLM_CALLS);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(invocation -> {
                    slotsTaken.countDown();
                    modelAnswers.await(5, TimeUnit.SECONDS);
                    return ResponseEntity.ok("{\"risk\":\"low\"}");
                });

        List<Future<ResponseEntity<String>>> running = new ArrayList<>();
        for (int i = 0; i < MlProxyService.MAX_CONCURRENT_LLM_CALLS; i++) {
            running.add(pool.submit(() -> service.analyzeClauseRisk("clausola")));
        }
        assertThat(slotsTaken.await(5, TimeUnit.SECONDS)).isTrue();

        ResponseEntity<String> refused = service.analyzeClauseRisk("clausola");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(refused.getBody()).contains("occupato");
        assertThat(meters.counter("bcm.ml.llm.busy").count()).isEqualTo(1.0);

        modelAnswers.countDown();
        for (Future<ResponseEntity<String>> call : running) {
            assertThat(call.get(5, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        // The slots were given back.
        assertThat(service.analyzeClauseRisk("clausola").getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
