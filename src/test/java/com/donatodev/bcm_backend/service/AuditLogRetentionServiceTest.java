package com.donatodev.bcm_backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.repository.AuditLogRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class AuditLogRetentionServiceTest {

    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks private AuditLogRetentionService auditLogRetentionService;

    @Nested
    @DisplayName("Unit Test: AuditLogRetentionService")
    class VerifyAuditLogRetentionService {

        @Test
        @DisplayName("Should delete entries older than the configured retention window")
        void shouldPurgeEntriesOlderThanRetentionWindow() {
            ReflectionTestUtils.setField(auditLogRetentionService, "retentionDays", 180);
            when(auditLogRepository.deleteByTimestampBefore(any())).thenReturn(42L);

            auditLogRetentionService.purgeExpiredAuditLogs();

            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(auditLogRepository).deleteByTimestampBefore(cutoffCaptor.capture());

            Instant expectedCutoff = Instant.now().minus(180, ChronoUnit.DAYS);
            long driftSeconds = Math.abs(expectedCutoff.getEpochSecond()
                    - cutoffCaptor.getValue().getEpochSecond());
            assertTrue(driftSeconds < 5, "cutoff should be ~180 days before now");
        }

        @Test
        @DisplayName("Should use the configured retention window, not a hardcoded default")
        void shouldRespectCustomRetentionWindow() {
            ReflectionTestUtils.setField(auditLogRetentionService, "retentionDays", 30);
            when(auditLogRepository.deleteByTimestampBefore(any())).thenReturn(0L);

            auditLogRetentionService.purgeExpiredAuditLogs();

            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(auditLogRepository).deleteByTimestampBefore(cutoffCaptor.capture());

            Instant expectedCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
            long driftSeconds = Math.abs(expectedCutoff.getEpochSecond()
                    - cutoffCaptor.getValue().getEpochSecond());
            assertTrue(driftSeconds < 5, "cutoff should be ~30 days before now");
        }
    }
}
