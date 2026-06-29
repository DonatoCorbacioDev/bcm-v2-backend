package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

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

import com.donatodev.bcm_backend.entity.MlResultCache;
import com.donatodev.bcm_backend.repository.MlResultCacheRepository;

@ExtendWith(MockitoExtension.class)
class MlCacheServiceTest {

    @Mock
    private MlResultCacheRepository repository;

    @InjectMocks
    private MlCacheService mlCacheService;

    private static final Long ORG = 1L;
    private static final String KEY = "FORECAST_3";

    private MlResultCache entry(LocalDateTime computedAt) {
        return MlResultCache.builder()
                .orgId(ORG).cacheKey(KEY)
                .jsonResult("{\"historical\":[]}")
                .computedAt(computedAt)
                .build();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("get()")
    @SuppressWarnings("unused")
    class Get {

        @Test
        @Order(1)
        @DisplayName("Returns empty when no entry exists")
        void returnsEmptyWhenMissing() {
            when(repository.findByOrgIdAndCacheKey(ORG, KEY)).thenReturn(Optional.empty());
            assertThat(mlCacheService.get(ORG, KEY)).isEmpty();
        }

        @Test
        @Order(2)
        @DisplayName("Returns cached value when entry is fresh (< 1h)")
        void returnsCachedValueWhenFresh() {
            when(repository.findByOrgIdAndCacheKey(ORG, KEY))
                    .thenReturn(Optional.of(entry(LocalDateTime.now().minusMinutes(30))));
            assertThat(mlCacheService.get(ORG, KEY)).contains("{\"historical\":[]}");
        }

        @Test
        @Order(3)
        @DisplayName("Returns empty when entry is stale (> 1h)")
        void returnsEmptyWhenStale() {
            when(repository.findByOrgIdAndCacheKey(ORG, KEY))
                    .thenReturn(Optional.of(entry(LocalDateTime.now().minusHours(2))));
            assertThat(mlCacheService.get(ORG, KEY)).isEmpty();
        }

        @Test
        @Order(4)
        @DisplayName("Returns empty without querying DB when orgId is null")
        void returnsEmptyWhenOrgIdNull() {
            assertThat(mlCacheService.get(null, KEY)).isEmpty();
            verifyNoInteractions(repository);
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("put()")
    @SuppressWarnings("unused")
    class Put {

        @Test
        @Order(5)
        @DisplayName("Saves a new entry when none exists")
        void savesNewEntry() {
            when(repository.findByOrgIdAndCacheKey(ORG, KEY)).thenReturn(Optional.empty());
            mlCacheService.put(ORG, KEY, "{\"ok\":true}");
            verify(repository).save(argThat(e ->
                    ORG.equals(e.getOrgId()) &&
                    KEY.equals(e.getCacheKey()) &&
                    "{\"ok\":true}".equals(e.getJsonResult()) &&
                    e.getComputedAt() != null));
        }

        @Test
        @Order(6)
        @DisplayName("Updates existing entry (upsert)")
        void updatesExistingEntry() {
            MlResultCache existing = entry(LocalDateTime.now().minusHours(2));
            when(repository.findByOrgIdAndCacheKey(ORG, KEY)).thenReturn(Optional.of(existing));
            mlCacheService.put(ORG, KEY, "new-json");
            verify(repository).save(argThat(e -> "new-json".equals(e.getJsonResult())));
        }

        @Test
        @Order(7)
        @DisplayName("Skips save when orgId is null")
        void skipsWhenOrgIdNull() {
            mlCacheService.put(null, KEY, "{}");
            verifyNoInteractions(repository);
        }

        @Test
        @Order(8)
        @DisplayName("Skips save when jsonResult is null")
        void skipsWhenJsonNull() {
            mlCacheService.put(ORG, KEY, null);
            verifyNoInteractions(repository);
        }
    }
}
