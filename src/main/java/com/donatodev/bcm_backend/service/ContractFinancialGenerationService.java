package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.dto.FinancialGenerationResultDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.FinancialValueSource;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.mapper.FinancialValueMapper;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;

/**
 * Derives a contract's {@link FinancialValues} rows from its optional
 * financial terms ({@link Contracts#getFinancialType()},
 * {@link Contracts#getAnnualValue()}, {@link Contracts#getBillingFrequency()})
 * instead of requiring them to be entered one row at a time.
 * <p>
 * <b>Period math</b>: periods are walked starting from the contract's
 * {@code startDate} in fixed steps of {@code 12 / periodsPerYear} months —
 * deliberately NOT anchored to calendar boundaries (e.g. Jan/Apr/Jul/Oct for
 * quarterly). Anchoring to a calendar boundary would produce a partial first
 * period whenever the contract doesn't start exactly on one, which this
 * service does not prorate by design (every generated period gets the full
 * {@code annualValue / periodsPerYear} amount) — starting from the
 * contract's own start date instead guarantees every period generated is a
 * full period.
 * <p>
 * <b>Past periods are frozen</b>: once a slot (contract, financial type,
 * month, year) is populated — MANUAL or GENERATED — generation never
 * deletes or recreates it if that slot's month/year is before today. Only
 * current/future slots can be replaced on regenerate. This means the very
 * first generation for a contract (nothing exists yet) fills the whole
 * range including past periods, but a later regenerate (e.g. after editing
 * the terms) never silently rewrites a month that's already "closed" in a
 * dashboard or report.
 * <p>
 * <b>Manual always wins</b>: a slot already occupied by a MANUAL row is
 * never touched — not deleted, not replaced. Editing a GENERATED row by
 * hand through the normal financial-value CRUD forces its {@code source} to
 * {@code MANUAL} (see {@link FinancialValueMapper}), so that edit
 * automatically "claims" the slot from future generation without any extra
 * bookkeeping here.
 * <p>
 * <b>Open-ended contracts</b> (no {@code endDate}) are capped at
 * {@link #OPEN_ENDED_HORIZON_YEARS} years from {@code startDate} — not from
 * "today", since that would make the row count depend on when generation
 * was last run rather than on the contract's own data. In this application,
 * {@code endDate} is enforced {@code @NotNull} on the normal contract
 * create/update path; the only way to reach a null {@code endDate} today is
 * instantiating a contract template with no {@code defaultDurationDays}.
 */
@Service
public class ContractFinancialGenerationService {

    private static final int OPEN_ENDED_HORIZON_YEARS = 5;

    private final FinancialValuesRepository financialValuesRepository;
    private final FinancialValueMapper financialValueMapper;
    private final MlCacheService mlCacheService;

    public ContractFinancialGenerationService(
            FinancialValuesRepository financialValuesRepository,
            FinancialValueMapper financialValueMapper,
            MlCacheService mlCacheService) {
        this.financialValuesRepository = financialValuesRepository;
        this.financialValueMapper = financialValueMapper;
        this.mlCacheService = mlCacheService;
    }

    /**
     * Returns {@code true} if the contract has all three financial-term
     * fields set (the feature is entirely opt-in — a contract missing any
     * one of them simply never triggers generation).
     */
    public boolean hasFinancialTerms(Contracts contract) {
        return contract.getFinancialType() != null
                && contract.getAnnualValue() != null
                && contract.getBillingFrequency() != null;
    }

    /**
     * Generates (or regenerates) the contract's financial values. Precondition:
     * {@link #hasFinancialTerms(Contracts)} is {@code true} — callers must
     * check before calling this.
     */
    @Transactional
    public FinancialGenerationResultDTO generate(Contracts contract) {
        int periodsPerYear = contract.getBillingFrequency().getPeriodsPerYear();
        double periodAmount = contract.getAnnualValue() / periodsPerYear;
        int stepMonths = 12 / periodsPerYear;

        LocalDate horizonEnd = contract.getEndDate() != null
                ? contract.getEndDate()
                : contract.getStartDate().plusYears(OPEN_ENDED_HORIZON_YEARS);

        List<FinancialValues> existing = financialValuesRepository
                .findByContract_IdAndFinancialType_Id(contract.getId(), contract.getFinancialType().getId());
        Map<YearMonth, FinancialValues> bySlot = existing.stream()
                .collect(Collectors.toMap(
                        fv -> YearMonth.of(fv.getYear(), fv.getMonth()),
                        Function.identity(),
                        (a, b) -> a,
                        HashMap::new));

        YearMonth today = YearMonth.now();
        List<FinancialValues> toDelete = existing.stream()
                .filter(fv -> fv.getSource() == FinancialValueSource.GENERATED)
                .filter(fv -> !YearMonth.of(fv.getYear(), fv.getMonth()).isBefore(today))
                .toList();
        financialValuesRepository.deleteAll(toDelete);
        toDelete.forEach(fv -> bySlot.remove(YearMonth.of(fv.getYear(), fv.getMonth())));

        List<FinancialValues> toCreate = new ArrayList<>();
        int skippedManual = 0;
        for (LocalDate cursor = contract.getStartDate(); !cursor.isAfter(horizonEnd); cursor = cursor.plusMonths(stepMonths)) {
            YearMonth slot = YearMonth.from(cursor);
            FinancialValues occupying = bySlot.get(slot);
            if (occupying != null) {
                if (occupying.getSource() == FinancialValueSource.MANUAL) {
                    skippedManual++;
                }
                continue;
            }
            toCreate.add(FinancialValues.builder()
                    .month(slot.getMonthValue())
                    .year(slot.getYear())
                    .financialAmount(periodAmount)
                    .financialType(contract.getFinancialType())
                    .businessArea(contract.getBusinessArea())
                    .contract(contract)
                    // Propagated explicitly, never inferred from the contract
                    // relationship — organization is its own FK on FinancialValues.
                    .organization(contract.getOrganization())
                    .source(FinancialValueSource.GENERATED)
                    .build());
        }
        financialValuesRepository.saveAll(toCreate);

        if (contract.getOrganization() != null) {
            mlCacheService.evictAllForOrg(contract.getOrganization().getId());
        }

        return new FinancialGenerationResultDTO(
                toCreate.size(),
                toDelete.size(),
                skippedManual,
                toCreate.stream().map(financialValueMapper::toDTO).toList());
    }
}
