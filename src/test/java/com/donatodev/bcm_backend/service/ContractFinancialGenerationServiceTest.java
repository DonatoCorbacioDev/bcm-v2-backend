package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.donatodev.bcm_backend.dto.FinancialGenerationResultDTO;
import com.donatodev.bcm_backend.dto.FinancialValueDTO;
import com.donatodev.bcm_backend.entity.BillingFrequency;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.FinancialValueSource;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.mapper.FinancialValueMapper;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;

/**
 * Unit tests for {@link ContractFinancialGenerationService}: period math,
 * manual-wins precedence, past-period freezing, and the open-ended horizon.
 */
@ExtendWith(MockitoExtension.class)
class ContractFinancialGenerationServiceTest {

    @Mock
    private FinancialValuesRepository financialValuesRepository;

    @Mock
    private FinancialValueMapper financialValueMapper;

    @Mock
    private MlCacheService mlCacheService;

    private ContractFinancialGenerationService generationService;

    private final FinancialTypes financialType = FinancialTypes.builder()
            .id(1L).name("Vendite").category(FinancialCategory.REVENUE).build();
    private final BusinessAreas businessArea = BusinessAreas.builder().id(1L).name("IT").build();
    private final Organization organization = Organization.builder().id(1L).name("Org").build();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        generationService = new ContractFinancialGenerationService(
                financialValuesRepository, financialValueMapper, mlCacheService);
        // The result's DTO list content doesn't matter for these tests, only
        // its size (verified via the `created` count) — stub a fixed dummy.
        when(financialValueMapper.toDTO(any(FinancialValues.class)))
                .thenReturn(new FinancialValueDTO(null, 1, 2025, 0.0, 1L, 1L, 1L, "Vendite", "IT", "Client", FinancialCategory.REVENUE));
    }

    private Contracts.ContractsBuilder baseContract(LocalDate start, LocalDate end) {
        return Contracts.builder()
                .id(10L)
                .startDate(start)
                .endDate(end)
                .businessArea(businessArea)
                .organization(organization)
                .financialType(financialType);
    }

    @Nested
    @DisplayName("Period math")
    class PeriodMath {

        @Test
        @DisplayName("36000/year MONTHLY over 12 months -> 12 rows of 3000")
        void monthlyGeneratesTwelveEqualRows() {
            Contracts contract = baseContract(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))
                    .annualValue(36000.0)
                    .billingFrequency(BillingFrequency.MONTHLY)
                    .build();
            when(financialValuesRepository.findByContract_IdAndFinancialType_Id(10L, 1L)).thenReturn(List.of());

            FinancialGenerationResultDTO result = generationService.generate(contract);

            assertEquals(12, result.created());
            assertEquals(0, result.skippedManual());
            ArgumentCaptor<List<FinancialValues>> captor = captorForSaveAll();
            List<FinancialValues> saved = captor.getValue();
            assertEquals(12, saved.size());
            assertTrue(saved.stream().allMatch(fv -> fv.getFinancialAmount() == 3000.0));
            assertTrue(saved.stream().allMatch(fv -> fv.getSource() == FinancialValueSource.GENERATED));
        }

        @Test
        @DisplayName("48000/year QUARTERLY over 12 months -> 4 rows of 12000")
        void quarterlyGeneratesFourEqualRows() {
            Contracts contract = baseContract(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))
                    .annualValue(48000.0)
                    .billingFrequency(BillingFrequency.QUARTERLY)
                    .build();
            when(financialValuesRepository.findByContract_IdAndFinancialType_Id(10L, 1L)).thenReturn(List.of());

            FinancialGenerationResultDTO result = generationService.generate(contract);

            assertEquals(4, result.created());
            List<FinancialValues> saved = captorForSaveAll().getValue();
            assertTrue(saved.stream().allMatch(fv -> fv.getFinancialAmount() == 12000.0));
        }

        @Test
        @DisplayName("periods walk from startDate, not from a calendar quarter boundary")
        void periodsAnchorToStartDateNotCalendar() {
            // Starts mid-quarter (Feb) — periods should land Feb/May/Aug/Nov, not Jan/Apr/Jul/Oct.
            Contracts contract = baseContract(LocalDate.of(2027, 2, 1), LocalDate.of(2028, 1, 31))
                    .annualValue(40000.0)
                    .billingFrequency(BillingFrequency.QUARTERLY)
                    .build();
            when(financialValuesRepository.findByContract_IdAndFinancialType_Id(10L, 1L)).thenReturn(List.of());

            generationService.generate(contract);

            List<FinancialValues> saved = captorForSaveAll().getValue();
            List<YearMonth> slots = saved.stream().map(fv -> YearMonth.of(fv.getYear(), fv.getMonth())).sorted().toList();
            assertEquals(
                    List.of(YearMonth.of(2027, 2), YearMonth.of(2027, 5), YearMonth.of(2027, 8), YearMonth.of(2027, 11)),
                    slots);
        }
    }

    @Nested
    @DisplayName("Manual-wins precedence")
    class ManualWins {

        @Test
        @DisplayName("a MANUAL row already occupying a slot is never overwritten")
        void skipsSlotsOccupiedByManualRows() {
            Contracts contract = baseContract(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31))
                    .annualValue(12000.0)
                    .billingFrequency(BillingFrequency.MONTHLY)
                    .build();
            FinancialValues manualRow = FinancialValues.builder()
                    .id(99L).month(2).year(2027).financialAmount(500.0)
                    .financialType(financialType).businessArea(businessArea).contract(contract)
                    .source(FinancialValueSource.MANUAL).build();
            when(financialValuesRepository.findByContract_IdAndFinancialType_Id(10L, 1L))
                    .thenReturn(List.of(manualRow));

            FinancialGenerationResultDTO result = generationService.generate(contract);

            assertEquals(2, result.created()); // Jan, Mar — Feb is occupied
            assertEquals(1, result.skippedManual());
            List<FinancialValues> saved = captorForSaveAll().getValue();
            assertTrue(saved.stream().noneMatch(fv -> fv.getMonth() == 2 && fv.getYear() == 2027));
            // The manual row is never part of the delete batch (only GENERATED rows are eligible).
            List<FinancialValues> deleted = captorForDeleteAll().getValue();
            assertTrue(deleted.isEmpty());
        }
    }

    @Nested
    @DisplayName("Past periods are frozen")
    class PastPeriodsFrozen {

        @Test
        @DisplayName("a GENERATED row for a past month is not deleted or recreated on regenerate")
        void pastGeneratedRowsSurviveRegeneration() {
            YearMonth lastMonth = YearMonth.now().minusMonths(1);
            LocalDate start = lastMonth.atDay(1);
            Contracts contract = baseContract(start, start.plusMonths(2))
                    .annualValue(3000.0)
                    .billingFrequency(BillingFrequency.MONTHLY)
                    .build();
            FinancialValues pastGenerated = FinancialValues.builder()
                    .id(50L).month(lastMonth.getMonthValue()).year(lastMonth.getYear())
                    .financialAmount(999.0) // deliberately stale, to prove it's untouched
                    .financialType(financialType).businessArea(businessArea).contract(contract)
                    .source(FinancialValueSource.GENERATED).build();
            when(financialValuesRepository.findByContract_IdAndFinancialType_Id(10L, 1L))
                    .thenReturn(List.of(pastGenerated));

            FinancialGenerationResultDTO result = generationService.generate(contract);

            // Past slot untouched: not deleted, not recreated, not counted as created.
            List<FinancialValues> deleted = captorForDeleteAll().getValue();
            assertTrue(deleted.isEmpty());
            List<FinancialValues> saved = captorForSaveAll().getValue();
            assertTrue(saved.stream().noneMatch(fv -> fv.getMonth() == lastMonth.getMonthValue() && fv.getYear() == lastMonth.getYear()));
        }

        @Test
        @DisplayName("a GENERATED row for the current or a future month IS deleted and recreated")
        void currentAndFutureGeneratedRowsAreRegenerated() {
            YearMonth thisMonth = YearMonth.now();
            LocalDate start = thisMonth.atDay(1);
            Contracts contract = baseContract(start, start.plusMonths(1))
                    .annualValue(2400.0)
                    .billingFrequency(BillingFrequency.MONTHLY)
                    .build();
            FinancialValues staleGenerated = FinancialValues.builder()
                    .id(51L).month(thisMonth.getMonthValue()).year(thisMonth.getYear())
                    .financialAmount(1.0)
                    .financialType(financialType).businessArea(businessArea).contract(contract)
                    .source(FinancialValueSource.GENERATED).build();
            when(financialValuesRepository.findByContract_IdAndFinancialType_Id(10L, 1L))
                    .thenReturn(List.of(staleGenerated));

            FinancialGenerationResultDTO result = generationService.generate(contract);

            assertEquals(1, result.regenerated());
            List<FinancialValues> deleted = captorForDeleteAll().getValue();
            assertEquals(1, deleted.size());
            assertEquals(staleGenerated, deleted.get(0));
            List<FinancialValues> saved = captorForSaveAll().getValue();
            assertTrue(saved.stream().anyMatch(fv -> fv.getMonth() == thisMonth.getMonthValue()
                    && fv.getYear() == thisMonth.getYear() && fv.getFinancialAmount() == 200.0));
        }
    }

    @Nested
    @DisplayName("Open-ended contracts")
    class OpenEnded {

        @Test
        @DisplayName("no end date caps generation at startDate + 5 years")
        void capsAtFiveYearsFromStart() {
            LocalDate start = LocalDate.of(2025, 1, 1);
            Contracts contract = baseContract(start, null)
                    .annualValue(12000.0)
                    .billingFrequency(BillingFrequency.ANNUAL)
                    .build();
            when(financialValuesRepository.findByContract_IdAndFinancialType_Id(10L, 1L)).thenReturn(List.of());

            FinancialGenerationResultDTO result = generationService.generate(contract);

            // 2025, 2026, 2027, 2028, 2029, 2030 (startDate + 5 years, inclusive) = 6 annual rows
            assertEquals(6, result.created());
        }
    }

    // ── Argument-captor helpers ───────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<FinancialValues>> captorForSaveAll() {
        ArgumentCaptor<List<FinancialValues>> captor = ArgumentCaptor.forClass(List.class);
        verify(financialValuesRepository, times(1)).saveAll(captor.capture());
        return captor;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<FinancialValues>> captorForDeleteAll() {
        ArgumentCaptor<List<FinancialValues>> captor = ArgumentCaptor.forClass(List.class);
        verify(financialValuesRepository, times(1)).deleteAll(captor.capture());
        return captor;
    }

}
