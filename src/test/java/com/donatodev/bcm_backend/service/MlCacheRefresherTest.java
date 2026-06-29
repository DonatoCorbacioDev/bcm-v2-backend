package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.repository.OrganizationRepository;

@ExtendWith(MockitoExtension.class)
class MlCacheRefresherTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MlProxyService mlProxyService;

    @Mock
    private MlCacheService mlCacheService;

    @InjectMocks
    private MlCacheRefresher mlCacheRefresher;

    private Organization org(Long id) {
        Organization o = new Organization();
        o.setId(id);
        return o;
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("refreshAll()")
    @SuppressWarnings("unused")
    class RefreshAll {

        @Test
        @Order(1)
        @DisplayName("Calls fetch and cache for each organization")
        void shouldRefreshAllOrgs() {
            when(organizationRepository.findAll()).thenReturn(List.of(org(1L), org(2L)));
            when(mlProxyService.fetchForecastRaw(anyInt(), anyLong()))
                    .thenReturn(ResponseEntity.ok("{\"forecast\":[]}"));
            when(mlProxyService.fetchAnomaliesRaw(anyLong()))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlCacheRefresher.refreshAll();

            // 2 orgs × 2 horizons (3 and 6) = 4 forecast calls
            verify(mlProxyService, times(4)).fetchForecastRaw(anyInt(), anyLong());
            // 2 orgs × 1 anomaly call
            verify(mlProxyService, times(2)).fetchAnomaliesRaw(anyLong());
            // 4 forecast saves + 2 anomaly saves
            verify(mlCacheService, times(6)).put(anyLong(), anyString(), anyString());
        }

        @Test
        @Order(2)
        @DisplayName("Continues refreshing remaining orgs when one fails")
        void shouldContinueOnOrgFailure() {
            when(organizationRepository.findAll()).thenReturn(List.of(org(1L), org(2L)));
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(1L)))
                    .thenThrow(new RuntimeException("ML down"));
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(2L)))
                    .thenReturn(ResponseEntity.ok("{}"));
            when(mlProxyService.fetchAnomaliesRaw(2L))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlCacheRefresher.refreshAll();

            // Org 2 should still be refreshed
            verify(mlProxyService, times(2)).fetchForecastRaw(anyInt(), eq(2L));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("refreshForOrg()")
    @SuppressWarnings("unused")
    class RefreshForOrg {

        @Test
        @Order(3)
        @DisplayName("Returns true on successful refresh")
        void returnsTrueOnSuccess() {
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(5L)))
                    .thenReturn(ResponseEntity.ok("{}"));
            when(mlProxyService.fetchAnomaliesRaw(5L))
                    .thenReturn(ResponseEntity.ok("[]"));

            assertTrue(mlCacheRefresher.refreshForOrg(5L));
        }

        @Test
        @Order(4)
        @DisplayName("Returns false when FastAPI throws")
        void returnsFalseOnException() {
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(9L)))
                    .thenThrow(new RuntimeException("timeout"));

            assertFalse(mlCacheRefresher.refreshForOrg(9L));
        }

        @Test
        @Order(5)
        @DisplayName("Does not save to cache when FastAPI returns 503")
        void doesNotCacheOnServiceUnavailable() {
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(3L)))
                    .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
            when(mlProxyService.fetchAnomaliesRaw(3L))
                    .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());

            mlCacheRefresher.refreshForOrg(3L);

            verify(mlCacheService, never()).put(anyLong(), anyString(), anyString());
        }
    }
}
