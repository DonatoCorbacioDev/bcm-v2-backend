package com.donatodev.bcm_backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.entity.SepaPaymentBatch;
import com.donatodev.bcm_backend.repository.SepaPaymentBatchRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class SepaPaymentRetentionServiceTest {

    @Mock private SepaPaymentBatchRepository batchRepository;
    @Mock private LocalStorageService localStorageService;

    @InjectMocks private SepaPaymentRetentionService sepaPaymentRetentionService;

    @Nested
    @DisplayName("Unit Test: SepaPaymentRetentionService")
    class VerifySepaPaymentRetentionService {

        @Test
        @DisplayName("Deletes each expired batch's file and then the batch rows, using the configured retention window")
        void shouldPurgeBatchesOlderThanRetentionWindow() {
            ReflectionTestUtils.setField(sepaPaymentRetentionService, "retentionDays", 730);
            SepaPaymentBatch batch1 = SepaPaymentBatch.builder().id(1L).storagePath("sepa/5/1/a.xml").build();
            SepaPaymentBatch batch2 = SepaPaymentBatch.builder().id(2L).storagePath("sepa/5/2/b.xml").build();
            when(batchRepository.findByCreatedAtBefore(any())).thenReturn(List.of(batch1, batch2));

            sepaPaymentRetentionService.purgeExpiredSepaPayments();

            verify(localStorageService).deleteDocument("sepa/5/1/a.xml");
            verify(localStorageService).deleteDocument("sepa/5/2/b.xml");
            verify(batchRepository).deleteAll(List.of(batch1, batch2));

            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(batchRepository).findByCreatedAtBefore(cutoffCaptor.capture());
            Instant expectedCutoff = Instant.now().minus(730, ChronoUnit.DAYS);
            long driftSeconds = Math.abs(expectedCutoff.getEpochSecond() - cutoffCaptor.getValue().getEpochSecond());
            assertTrue(driftSeconds < 5, "cutoff should be ~730 days before now");
        }

        @Test
        @DisplayName("Does nothing when no batch is older than the retention window")
        void shouldDoNothingWhenNothingExpired() {
            ReflectionTestUtils.setField(sepaPaymentRetentionService, "retentionDays", 730);
            when(batchRepository.findByCreatedAtBefore(any())).thenReturn(List.of());

            sepaPaymentRetentionService.purgeExpiredSepaPayments();

            verify(localStorageService, never()).deleteDocument(any());
            verify(batchRepository).deleteAll(List.of());
        }

        @Test
        @DisplayName("Uses the configured retention window, not a hardcoded default")
        void shouldRespectCustomRetentionWindow() {
            ReflectionTestUtils.setField(sepaPaymentRetentionService, "retentionDays", 30);
            when(batchRepository.findByCreatedAtBefore(any())).thenReturn(List.of());

            sepaPaymentRetentionService.purgeExpiredSepaPayments();

            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(batchRepository).findByCreatedAtBefore(cutoffCaptor.capture());
            Instant expectedCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
            long driftSeconds = Math.abs(expectedCutoff.getEpochSecond() - cutoffCaptor.getValue().getEpochSecond());
            assertTrue(driftSeconds < 5, "cutoff should be ~30 days before now");
        }
    }
}
