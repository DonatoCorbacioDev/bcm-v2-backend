/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Computes and applies the link between an uploaded {@link ElectronicInvoice}
 * and the {@link FinancialValues} row it corresponds to.
 * <p>
 * <b>Two-step design</b>: an invoice and its contract's financial values
 * always share the same counterparty (both are scoped to the same
 * contract), so the counterparty check is a one-time sanity gate rather than
 * part of the per-row score. If the invoice's supplier doesn't correspond to
 * the contract's counterparty at all (checked by VAT number when both are
 * present, otherwise by name), no candidate is scored at all — see
 * {@link InvoiceMatchStatus#COUNTERPARTY_MISMATCH}. Otherwise every
 * candidate row for the contract is scored on amount closeness (weight
 * {@value #AMOUNT_WEIGHT}) and date proximity (weight {@value #DATE_WEIGHT});
 * the best-scoring row at or above {@value #SUGGESTION_THRESHOLD} is
 * suggested.
 * <p>
 * <b>Human confirmation required</b>: a computed suggestion never becomes
 * {@code CONFIRMED} on its own — only {@link #confirmMatch} does that.
 * {@link #recomputeForContract} deliberately skips invoices already
 * {@code CONFIRMED} or {@code REJECTED}, so a human decision is never
 * silently overwritten by a later recompute.
 * <p>
 * No fuzzy-matching library is used — string comparison is a manual
 * normalize-and-compare, consistent with the deterministic (non-LLM) style
 * already used for IBAN/VAT parsing in {@code FatturaPaXmlParserService}.
 */
@Service
public class InvoiceMatchingService {

    private static final double AMOUNT_WEIGHT = 0.6;
    private static final double DATE_WEIGHT = 0.4;
    private static final double SUGGESTION_THRESHOLD = 0.4;
    private static final Pattern COUNTRY_PREFIX = Pattern.compile("^[A-Z]{2}(\\d+)$");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");

    private final ElectronicInvoiceRepository invoiceRepository;
    private final FinancialValuesRepository financialValuesRepository;
    private final UsersRepository usersRepository;

    public InvoiceMatchingService(ElectronicInvoiceRepository invoiceRepository,
                                   FinancialValuesRepository financialValuesRepository,
                                   UsersRepository usersRepository) {
        this.invoiceRepository = invoiceRepository;
        this.financialValuesRepository = financialValuesRepository;
        this.usersRepository = usersRepository;
    }

    /**
     * Computes a suggestion for the given invoice and writes it directly onto
     * the entity (the caller is responsible for saving it). A no-op if the
     * invoice is already {@code CONFIRMED} or {@code REJECTED} — those are
     * final human decisions, never recomputed.
     */
    public void computeSuggestion(ElectronicInvoice invoice) {
        if (invoice.getMatchStatus() == InvoiceMatchStatus.CONFIRMED
                || invoice.getMatchStatus() == InvoiceMatchStatus.REJECTED) {
            return;
        }

        Contracts contract = invoice.getContract();
        Counterparty counterparty = contract.getCounterparty();
        if (counterparty == null || !counterpartyMatches(invoice, counterparty)) {
            invoice.setMatchStatus(InvoiceMatchStatus.COUNTERPARTY_MISMATCH);
            invoice.setMatchedFinancialValue(null);
            invoice.setMatchConfidence(null);
            return;
        }

        FinancialValues best = null;
        double bestScore = 0.0;
        for (FinancialValues candidate : financialValuesRepository.findByContractId(contract.getId())) {
            if (isConfirmedToAnotherInvoice(candidate, invoice)) {
                continue;
            }
            double score = AMOUNT_WEIGHT * amountScore(invoice.getTotalAmount(), candidate.getFinancialAmount())
                    + DATE_WEIGHT * dateScore(invoice.getInvoiceDate(), candidate.getMonth(), candidate.getYear());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != null && bestScore >= SUGGESTION_THRESHOLD) {
            invoice.setMatchStatus(InvoiceMatchStatus.SUGGESTED);
            invoice.setMatchedFinancialValue(best);
            invoice.setMatchConfidence(bestScore);
        } else {
            invoice.setMatchStatus(InvoiceMatchStatus.UNMATCHED);
            invoice.setMatchedFinancialValue(null);
            invoice.setMatchConfidence(null);
        }
    }

    /**
     * Confirms a match — either the one already suggested ({@code financialValueIdOrNull == null})
     * or a manual override of it. Rejects if the target row is already
     * confirmed against a different invoice, or belongs to a different
     * contract than the invoice.
     */
    @Transactional
    public ElectronicInvoice confirmMatch(ElectronicInvoice invoice, Long financialValueIdOrNull, String username) {
        FinancialValues target = financialValueIdOrNull != null
                ? financialValuesRepository.findById(financialValueIdOrNull)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Valore finanziario non trovato: " + financialValueIdOrNull))
                : invoice.getMatchedFinancialValue();
        if (target == null) {
            throw new IllegalArgumentException("Nessun abbinamento da confermare per la fattura " + invoice.getId());
        }
        if (!target.getContract().getId().equals(invoice.getContract().getId())) {
            throw new IllegalArgumentException(
                    "Il valore finanziario non appartiene allo stesso contratto della fattura");
        }
        invoiceRepository.findByMatchedFinancialValue_IdAndMatchStatus(target.getId(), InvoiceMatchStatus.CONFIRMED)
                .filter(other -> !other.getId().equals(invoice.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException(
                            "Il valore finanziario è già confermato dalla fattura " + other.getId());
                });

        Users user = usersRepository.findByUsername(username).orElse(null);

        invoice.setMatchedFinancialValue(target);
        invoice.setMatchConfidence(invoice.getMatchConfidence() != null ? invoice.getMatchConfidence() : 1.0);
        invoice.setMatchStatus(InvoiceMatchStatus.CONFIRMED);
        invoice.setMatchedAt(Instant.now());
        invoice.setMatchedByUser(user);
        return invoiceRepository.save(invoice);
    }

    /** Marks a suggestion as explicitly dismissed — final, never reconsidered by a later recompute. */
    @Transactional
    public ElectronicInvoice rejectMatch(ElectronicInvoice invoice) {
        invoice.setMatchStatus(InvoiceMatchStatus.REJECTED);
        invoice.setMatchedFinancialValue(null);
        invoice.setMatchConfidence(null);
        invoice.setMatchedAt(null);
        invoice.setMatchedByUser(null);
        return invoiceRepository.save(invoice);
    }

    /** Re-runs {@link #computeSuggestion} on every invoice of the contract not already CONFIRMED/REJECTED. */
    @Transactional
    public List<ElectronicInvoice> recomputeForContract(Long contractId) {
        List<ElectronicInvoice> toRecompute = invoiceRepository.findByContractIdAndMatchStatusIn(
                contractId,
                List.of(InvoiceMatchStatus.UNMATCHED, InvoiceMatchStatus.SUGGESTED, InvoiceMatchStatus.COUNTERPARTY_MISMATCH));
        toRecompute.forEach(this::computeSuggestion);
        return invoiceRepository.saveAll(toRecompute);
    }

    private boolean isConfirmedToAnotherInvoice(FinancialValues candidate, ElectronicInvoice invoice) {
        return invoiceRepository.findByMatchedFinancialValue_IdAndMatchStatus(candidate.getId(), InvoiceMatchStatus.CONFIRMED)
                .filter(other -> !other.getId().equals(invoice.getId()))
                .isPresent();
    }

    private boolean counterpartyMatches(ElectronicInvoice invoice, Counterparty counterparty) {
        String invoiceVat = normalizeVat(invoice.getSupplierVatNumber());
        String counterpartyVat = normalizeVat(counterparty.getVatNumber());
        if (invoiceVat != null && counterpartyVat != null) {
            return invoiceVat.equals(counterpartyVat);
        }
        String invoiceName = normalizeName(invoice.getSupplierName());
        String counterpartyName = normalizeName(counterparty.getName());
        if (invoiceName.isEmpty() || counterpartyName.isEmpty()) {
            return false;
        }
        return invoiceName.contains(counterpartyName) || counterpartyName.contains(invoiceName);
    }

    private String normalizeVat(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String upper = raw.replace(" ", "").toUpperCase(Locale.ROOT);
        Matcher matcher = COUNTRY_PREFIX.matcher(upper);
        return matcher.matches() ? matcher.group(1) : upper;
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        return NON_ALPHANUMERIC.matcher(raw.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private double amountScore(BigDecimal invoiceAmount, double financialAmount) {
        if (invoiceAmount == null || financialAmount == 0.0) {
            return 0.0;
        }
        double relativeDiff = Math.abs(invoiceAmount.doubleValue() - financialAmount) / financialAmount;
        return Math.max(0.0, 1.0 - relativeDiff);
    }

    private double dateScore(LocalDate invoiceDate, int month, int year) {
        if (invoiceDate == null) {
            return 0.0;
        }
        long monthsApart = Math.abs(ChronoUnit.MONTHS.between(YearMonth.of(year, month), YearMonth.from(invoiceDate)));
        if (monthsApart == 0) {
            return 1.0;
        }
        if (monthsApart == 1) {
            return 0.6;
        }
        if (monthsApart == 2) {
            return 0.3;
        }
        return 0.0;
    }
}
