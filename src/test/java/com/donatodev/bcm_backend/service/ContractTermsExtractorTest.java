/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.donatodev.bcm_backend.dto.ContractTermsDTO.Field;
import com.donatodev.bcm_backend.dto.ContractTermsDTO.Term;

/** Edge cases around the corpus evaluation (ContractTermsExtractorEvaluationTest). */
@DisplayName("ContractTermsExtractor edge cases")
class ContractTermsExtractorTest {

    private final ContractTermsExtractor extractor = new ContractTermsExtractor();

    private Term only(List<Term> terms, Field field) {
        return terms.stream().filter(t -> t.field() == field).findFirst().orElse(null);
    }

    @Test
    @DisplayName("No text, no terms")
    void emptyText() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("   ")).isEmpty();
    }

    @Test
    @DisplayName("Each term carries the sentence it comes from and how the text says it")
    void keepsSourceSentence() {
        List<Term> terms = extractor.extract("Art. 3 - Rinnovo.\n\nIl contratto si rinnova tacitamente "
                + "salvo disdetta con preavviso di tre mesi.");

        Term notice = only(terms, Field.NOTICE_DAYS);
        assertThat(notice.value()).isEqualTo("90");
        assertThat(notice.asWritten()).isEqualTo("3 mesi");
        assertThat(notice.sentence()).isEqualTo("Il contratto si rinnova tacitamente salvo disdetta con preavviso di tre mesi.");
        assertThat(only(terms, Field.AUTO_RENEW).value()).isEqualTo("true");
    }

    @Test
    @DisplayName("A one-month notice reads as \"1 mese\"")
    void singularMonth() {
        Term notice = only(extractor.extract("Salvo disdetta con preavviso di un mese."), Field.NOTICE_DAYS);
        assertThat(notice.value()).isEqualTo("30");
        assertThat(notice.asWritten()).isEqualTo("1 mese");
    }

    @Test
    @DisplayName("A date that doesn't exist is not a value")
    void impossibleDate() {
        assertThat(only(extractor.extract("Il contratto decorre dal 31/02/2026."), Field.START_DATE)).isNull();
    }

    @Test
    @DisplayName("A start after the end means a misreading: neither date is proposed")
    void startAfterEnd() {
        List<Term> terms = extractor.extract("Il servizio decorre dal 1 marzo 2027. Il contratto scade il 1 marzo 2026.");
        assertThat(only(terms, Field.START_DATE)).isNull();
        assertThat(only(terms, Field.END_DATE)).isNull();
    }

    @Test
    @DisplayName("A renewal for a triennio is 36 months; years count as 12 months each")
    void multiYearRenewals() {
        assertThat(only(extractor.extract("Salvo disdetta il contratto si rinnova per un ulteriore triennio."),
                Field.RENEWAL_MONTHS).value()).isEqualTo("36");
        assertThat(only(extractor.extract("Il contratto si rinnova tacitamente per ulteriori 2 anni."),
                Field.RENEWAL_MONTHS).value()).isEqualTo("24");
    }

    @Test
    @DisplayName("Reads the public demo's sample PDF (scripts/demo_contract_pdf.py) end to end, through PDFBox")
    void readsDemoPdf() throws java.io.IOException {
        byte[] pdf;
        try (java.io.InputStream in = getClass().getResourceAsStream("/contract-terms/demo-maintenance.pdf")) {
            pdf = in.readAllBytes();
        }
        // Text-based PDF: OCR is never reached, so no OcrService is needed.
        String text = new PdfBoxService(null).extractRawText(pdf);

        java.util.Map<Field, String> values = new java.util.EnumMap<>(Field.class);
        extractor.extract(text).forEach(t -> values.put(t.field(), t.value()));
        assertThat(values).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                Field.AUTO_RENEW, "true", Field.NOTICE_DAYS, "90", Field.RENEWAL_MONTHS, "12",
                Field.START_DATE, "2025-11-23", Field.END_DATE, "2026-11-23"));
    }

    @Test
    @DisplayName("A very long sentence is cut for display, not dropped")
    void longSentence() {
        String filler = "parola ".repeat(120);
        Term renew = only(extractor.extract("Il contratto si rinnova tacitamente " + filler + "fine."), Field.AUTO_RENEW);
        assertThat(renew.sentence()).endsWith("…").hasSizeLessThanOrEqualTo(401);
    }
}
