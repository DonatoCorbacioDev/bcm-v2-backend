/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.donatodev.bcm_backend.dto.ContractTermsDTO.Field;
import com.donatodev.bcm_backend.dto.ContractTermsDTO.Term;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Measures the extractor against the hand-written corpus
 * (src/test/resources/contract-terms/corpus.json) the way one measures a
 * model: per field, precision (of the values proposed, how many are right)
 * and recall (of the values the text states, how many are found).
 * <p>
 * The bar for showing the feature to users is no wrong value at all
 * (precision 1.0) and at least 80% of the stated values found. "dev" cases
 * were available while writing the rules; "holdout" cases were not tuned on,
 * so they are the honest estimate. Both are written by the same author, so
 * neither replaces real contracts.
 */
@DisplayName("ContractTermsExtractor evaluation on the corpus")
class ContractTermsExtractorEvaluationTest {

    private static final Map<Field, String> CORPUS_KEYS = Map.of(
            Field.AUTO_RENEW, "autoRenew", Field.NOTICE_DAYS, "noticeDays",
            Field.RENEWAL_MONTHS, "renewalMonths", Field.START_DATE, "startDate", Field.END_DATE, "endDate");

    private final ContractTermsExtractor extractor = new ContractTermsExtractor();

    /** True/false positives and false negatives for one field. */
    private static final class Counts {
        int truePositive;
        int falsePositive;
        int falseNegative;

        double precision() {
            int proposed = truePositive + falsePositive;
            return proposed == 0 ? 1.0 : (double) truePositive / proposed;
        }

        double recall() {
            int stated = truePositive + falseNegative;
            return stated == 0 ? 1.0 : (double) truePositive / stated;
        }
    }

    private record Result(Map<Field, Counts> perField, Counts total, List<String> mistakes) {
    }

    private Result evaluate(String split) throws IOException {
        JsonNode corpus;
        try (InputStream in = getClass().getResourceAsStream("/contract-terms/corpus.json")) {
            corpus = new ObjectMapper().readTree(in);
        }
        Map<Field, Counts> perField = new EnumMap<>(Field.class);
        Counts total = new Counts();
        List<String> mistakes = new ArrayList<>();
        for (JsonNode item : corpus.get(split)) {
            Map<Field, String> proposed = new EnumMap<>(Field.class);
            for (Term term : extractor.extract(item.get("text").asString())) {
                proposed.put(term.field(), term.value());
            }
            JsonNode expected = item.get("expected");
            for (Field field : Field.values()) {
                JsonNode stated = expected.get(CORPUS_KEYS.get(field));
                String want = stated == null ? null : stated.asString();
                String got = proposed.get(field);
                Counts counts = perField.computeIfAbsent(field, f -> new Counts());
                if (got != null && got.equals(want)) {
                    counts.truePositive++;
                    total.truePositive++;
                } else if (got != null || want != null) {
                    if (got != null) {
                        counts.falsePositive++;
                        total.falsePositive++;
                    }
                    if (want != null) {
                        counts.falseNegative++;
                        total.falseNegative++;
                    }
                    mistakes.add(item.get("id").asString() + " " + field + ": got " + got + ", expected " + want);
                }
            }
        }
        return new Result(perField, total, mistakes);
    }

    private static void print(String split, Result result) {
        StringBuilder out = new StringBuilder("\n== ").append(split).append(" ==\n");
        out.append(String.format("%-15s %9s %7s%n", "field", "precision", "recall"));
        result.perField().forEach((field, c) ->
                out.append(String.format("%-15s %9.2f %7.2f%n", field, c.precision(), c.recall())));
        out.append(String.format("%-15s %9.2f %7.2f%n", "ALL", result.total().precision(), result.total().recall()));
        result.mistakes().forEach(m -> out.append("  ").append(m).append('\n'));
        System.out.print(out);
    }

    @Test
    @DisplayName("dev split: no wrong value, at least 80% of stated values found")
    void devSplit() throws IOException {
        Result result = evaluate("dev");
        print("dev", result);
        assertThat(result.total().precision()).as("precision").isEqualTo(1.0);
        assertThat(result.total().recall()).as("recall").isGreaterThanOrEqualTo(0.8);
    }

    @Test
    @DisplayName("holdout split (not tuned on): no wrong value, at least 80% of stated values found")
    void holdoutSplit() throws IOException {
        Result result = evaluate("holdout");
        print("holdout", result);
        assertThat(result.total().precision()).as("precision").isEqualTo(1.0);
        assertThat(result.total().recall()).as("recall").isGreaterThanOrEqualTo(0.8);
    }
}
