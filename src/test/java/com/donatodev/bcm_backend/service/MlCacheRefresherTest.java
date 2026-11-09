package com.donatodev.bcm_backend.service;

import static org.mockito.ArgumentMatchers.any;
import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
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

    private Organization org(UUID id) {
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
            when(organizationRepository.findAll()).thenReturn(List.of(org(uid(1)), org(uid(2))));
            when(mlProxyService.fetchForecastRaw(anyInt(), any(UUID.class)))
                    .thenReturn(ResponseEntity.ok("{\"forecast\":[]}"));
            when(mlProxyService.fetchAnomaliesRaw(any(UUID.class)))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlCacheRefresher.refreshAll();

            // 2 orgs × 2 horizons (3 and 6) = 4 forecast calls
            verify(mlProxyService, times(4)).fetchForecastRaw(anyInt(), any(UUID.class));
            // 2 orgs × 1 anomaly call
            verify(mlProxyService, times(2)).fetchAnomaliesRaw(any(UUID.class));
            // 4 forecast saves + 2 anomaly saves
            verify(mlCacheService, times(6)).put(any(UUID.class), anyString(), anyString());
        }

        @Test
        @Order(2)
        @DisplayName("Continues refreshing remaining orgs when one fails")
        void shouldContinueOnOrgFailure() {
            when(organizationRepository.findAll()).thenReturn(List.of(org(uid(1)), org(uid(2))));
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(uid(1))))
                    .thenThrow(new RuntimeException("ML down"));
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(uid(2))))
                    .thenReturn(ResponseEntity.ok("{}"));
            when(mlProxyService.fetchAnomaliesRaw(uid(2)))
                    .thenReturn(ResponseEntity.ok("[]"));

            mlCacheRefresher.refreshAll();

            // Org 2 should still be refreshed
            verify(mlProxyService, times(2)).fetchForecastRaw(anyInt(), eq(uid(2)));
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
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(uid(5))))
                    .thenReturn(ResponseEntity.ok("{}"));
            when(mlProxyService.fetchAnomaliesRaw(uid(5)))
                    .thenReturn(ResponseEntity.ok("[]"));

            assertTrue(mlCacheRefresher.refreshForOrg(uid(5)));
        }

        @Test
        @Order(4)
        @DisplayName("Returns false when FastAPI throws")
        void returnsFalseOnException() {
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(uid(9))))
                    .thenThrow(new RuntimeException("timeout"));

            assertFalse(mlCacheRefresher.refreshForOrg(uid(9)));
        }

        @Test
        @Order(41)
        @DisplayName("Returns false when FastAPI throws with no message")
        void returnsFalseOnExceptionWithNullMessage() {
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(uid(9))))
                    .thenThrow(new RuntimeException());

            assertFalse(mlCacheRefresher.refreshForOrg(uid(9)));
        }

        @Test
        @Order(5)
        @DisplayName("Does not save to cache when FastAPI returns 503")
        void doesNotCacheOnServiceUnavailable() {
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(uid(3))))
                    .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
            when(mlProxyService.fetchAnomaliesRaw(uid(3)))
                    .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());

            mlCacheRefresher.refreshForOrg(uid(3));

            verify(mlCacheService, never()).put(any(UUID.class), anyString(), anyString());
        }

        @Test
        @Order(6)
        @DisplayName("Does not save to cache when FastAPI returns a 2xx response with a null body")
        void doesNotCacheOnNullBody() {
            when(mlProxyService.fetchForecastRaw(anyInt(), eq(uid(4))))
                    .thenReturn(ResponseEntity.status(HttpStatus.OK).body(null));
            when(mlProxyService.fetchAnomaliesRaw(uid(4)))
                    .thenReturn(ResponseEntity.status(HttpStatus.OK).body(null));

            mlCacheRefresher.refreshForOrg(uid(4));

            verify(mlCacheService, never()).put(any(UUID.class), anyString(), anyString());
        }
    }
}
