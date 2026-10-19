/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Manual evaluation tool for {@link SemanticSearchService}'s embedding-based
 * ranking against REAL embeddings ({@code nomic-embed-text} via Ollama) -
 * not mocked like {@link SemanticSearchServiceTest}, which only ever
 * exercises {@code cosineSimilarity}'s ranking logic against hand-picked
 * vectors. Nothing in the automated suite today checks whether
 * {@code nomic-embed-text} actually produces a sensible ranking for real
 * Italian contract-clause text - this tool exists to measure that.
 * <p>
 * Deliberately named without a {@code Test}/{@code Tests}/{@code IT} suffix
 * so Surefire ({@code **}{@code /*Test.java}, {@code **}{@code /*Tests.java}
 * per pom.xml) and Failsafe ({@code **}{@code /*IT.java}) never pick it up
 * automatically - same "standalone dev tool, run by hand" posture as
 * {@code bcm-v2-ml}'s {@code scripts/eval_*.py}.
 * <p>
 * Run via: {@code mvn test -Dtest=SemanticSearchEval#run} (requires Ollama
 * running locally with {@code nomic-embed-text} pulled - see
 * {@code application.properties}' {@code spring.ai.ollama.*} keys).
 */
@SpringBootTest
@ActiveProfiles("test")
class SemanticSearchEval {

    @Autowired
    private EmbeddingModel embeddingModel;

    private record Scenario(String query, String expectedDocLabel) {
    }

    private static final Map<String, String> DOCUMENTS = Map.of(
            "recesso", "Il presente contratto prevede una clausola di recesso unilaterale con "
                    + "preavviso di novanta giorni a favore del fornitore.",
            "canone", "Il canone di locazione annuale e' fissato in ventiquattromila euro, "
                    + "da versare in quattro rate trimestrali.",
            "foro", "In caso di controversia, le parti si rimettono in via esclusiva al Foro di Milano.",
            "riservatezza", "Il fornitore si impegna a mantenere la massima riservatezza su tutte le "
                    + "informazioni tecniche e commerciali scambiate durante l'esecuzione del contratto."
    );

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("clausola di recesso dal contratto", "recesso"),
            new Scenario("importo del canone annuale da pagare", "canone"),
            new Scenario("foro competente in caso di controversia", "foro"),
            new Scenario("obbligo di riservatezza delle informazioni", "riservatezza")
    );

    @Test
    void run() {
        Map<String, float[]> docEmbeddings = new LinkedHashMap<>();
        DOCUMENTS.forEach((label, text) -> docEmbeddings.put(label, embeddingModel.embed(text)));

        int correctTop1 = 0;
        double reciprocalRankSum = 0;

        for (Scenario scenario : SCENARIOS) {
            float[] queryEmbedding = embeddingModel.embed(scenario.query());

            List<Map.Entry<String, Double>> ranked = docEmbeddings.entrySet().stream()
                    .map(e -> Map.entry(e.getKey(), SemanticSearchService.cosineSimilarity(queryEmbedding, e.getValue())))
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .toList();

            int rank = 1;
            for (Map.Entry<String, Double> entry : ranked) {
                if (entry.getKey().equals(scenario.expectedDocLabel())) {
                    break;
                }
                rank++;
            }
            boolean top1 = rank == 1;
            if (top1) {
                correctTop1++;
            }
            reciprocalRankSum += 1.0 / rank;

            System.out.printf("Query: \"%s\" -> expected \"%s\", ranked #%d (top1=%s)%n",
                    scenario.query(), scenario.expectedDocLabel(), rank, top1);
            ranked.forEach(e -> System.out.printf("   %-14s %.4f%n", e.getKey(), e.getValue()));
        }

        double top1Accuracy = (double) correctTop1 / SCENARIOS.size();
        double mrr = reciprocalRankSum / SCENARIOS.size();
        System.out.printf("%n=== Summary === top1Accuracy=%.0f%%  MRR=%.3f%n", top1Accuracy * 100, mrr);
    }
}
