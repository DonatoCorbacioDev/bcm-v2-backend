/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.SemanticSearchResultDTO;
import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.SemanticSearchUnavailableException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Semantic search over contract documents: an embedding is generated (via
 * Ollama, through Spring AI) once per document when its text is extracted,
 * stored as JSON on {@link ContractDocument#getEmbedding()}, and compared to
 * the query embedding by cosine similarity at search time. No vector store —
 * MySQL has no pgvector equivalent, and at the document volume of a single
 * tenant, comparing in memory is simpler and fast enough.
 */
@Service
public class SemanticSearchService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticSearchService.class);

    /** Conservative character cap so the extracted text stays within
     * nomic-embed-text's context window regardless of document length. */
    private static final int MAX_EMBEDDING_INPUT_CHARS = 6000;

    private static final String CRLF_REGEX = "[\r\n]";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final float[] EMPTY_EMBEDDING = new float[0];

    private final ContractDocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CurrentUserResolver currentUserResolver;

    public SemanticSearchService(ContractDocumentRepository documentRepository,
                                  EmbeddingModel embeddingModel,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry,
                                  CurrentUserResolver currentUserResolver) {
        this.documentRepository = documentRepository;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * Best-effort: a failure here (Ollama unreachable, model not pulled)
     * must not break the text-extraction action it rides along with.
     */
    public void generateAndStoreEmbedding(ContractDocument document, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String input = text.length() > MAX_EMBEDDING_INPUT_CHARS
                ? text.substring(0, MAX_EMBEDDING_INPUT_CHARS)
                : text;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            float[] embedding = embeddingModel.embed(input);
            document.setEmbedding(objectMapper.writeValueAsString(embedding));
            documentRepository.save(document);
            sample.stop(Timer.builder("bcm.embedding.generate").tag("outcome", "success").register(meterRegistry));
        } catch (Exception e) {
            sample.stop(Timer.builder("bcm.embedding.generate").tag("outcome", "error").register(meterRegistry));
            logger.warn("Embedding generation failed for document {}: {}", document.getId(), safeMessage(e));
        }
    }

    public List<SemanticSearchResultDTO> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Long orgId = TenantContext.require();
        List<ContractDocument> candidates = visibleToCurrentUser(
                documentRepository.findByOrgIdAndEmbeddingIsNotNull(orgId));
        if (candidates.isEmpty()) {
            return List.of();
        }

        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingModel.embed(query);
        } catch (Exception e) {
            logger.warn("Query embedding failed: {}", safeMessage(e));
            throw new SemanticSearchUnavailableException("Impossibile generare l'embedding della query", e);
        }

        List<SemanticSearchResultDTO> results = new ArrayList<>();
        for (ContractDocument doc : candidates) {
            float[] docEmbedding = readEmbedding(doc);
            if (docEmbedding.length == 0) {
                continue;
            }
            double score = cosineSimilarity(queryEmbedding, docEmbedding);
            results.add(new SemanticSearchResultDTO(
                    doc.getContract().getId(),
                    doc.getContract().getContractNumber(),
                    doc.getContract().getCounterparty().getName(),
                    doc.getId(),
                    doc.getFileName(),
                    score));
        }

        results.sort(Comparator.comparingDouble(SemanticSearchResultDTO::score).reversed());
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    /**
     * Same visibility as the contract detail page: an ADMIN sees every
     * document of the organization, a MANAGER only those of contracts
     * assigned to them, and a user who is neither sees nothing (fail closed).
     */
    private List<ContractDocument> visibleToCurrentUser(List<ContractDocument> documents) {
        if (documents.isEmpty()) {
            return documents;
        }
        Users user = currentUserResolver.resolve();
        if (ROLE_ADMIN.equals(user.getRole().getRole())) {
            return documents;
        }
        Long managerId = user.getManager() != null ? user.getManager().getId() : null;
        if (managerId == null) {
            return List.of();
        }
        return documents.stream()
                .filter(doc -> doc.getContract().getManager() != null
                        && managerId.equals(doc.getContract().getManager().getId()))
                .toList();
    }

    private float[] readEmbedding(ContractDocument doc) {
        try {
            return objectMapper.readValue(doc.getEmbedding(), float[].class);
        } catch (JacksonException e) {
            logger.warn("Corrupt embedding for document {}, skipping", doc.getId());
            return EMPTY_EMBEDDING;
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? null : message.replaceAll(CRLF_REGEX, "_");
    }

    /** Package-private (not private) so the unit test can call it directly
     * without going through Ollama. */
    static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Embedding dimension mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
