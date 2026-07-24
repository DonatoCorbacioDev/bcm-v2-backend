package com.donatodev.bcm_backend.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.SemanticSearchResultDTO;
import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class SemanticSearchServiceTest {

    @Mock private ContractDocumentRepository documentRepository;
    @Mock private EmbeddingModel embeddingModel;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    private SimpleMeterRegistry meterRegistry;
    private SemanticSearchService semanticSearchService;

    private static final long ORG_ID = 1L;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        semanticSearchService = new SemanticSearchService(documentRepository, embeddingModel, objectMapper, meterRegistry);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Contracts fakeContract() {
        Contracts c = new Contracts();
        c.setId(5L);
        c.setContractNumber("CTR-001");
        c.setCustomerName("Acme");
        return c;
    }

    private ContractDocument fakeDoc(String embeddingJson) {
        ContractDocument doc = new ContractDocument();
        doc.setId(10L);
        doc.setFileName("contract.pdf");
        doc.setOrgId(ORG_ID);
        doc.setContract(fakeContract());
        doc.setEmbedding(embeddingJson);
        return doc;
    }

    @Nested
    @DisplayName("cosineSimilarity")
    class CosineSimilarity {

        @Test
        @DisplayName("identical vectors score 1.0")
        void identicalVectorsScoreOne() {
            float[] a = {1f, 2f, 3f};
            assertEquals(1.0, SemanticSearchService.cosineSimilarity(a, a), 1e-9);
        }

        @Test
        @DisplayName("orthogonal vectors score 0.0")
        void orthogonalVectorsScoreZero() {
            float[] a = {1f, 0f};
            float[] b = {0f, 1f};
            assertEquals(0.0, SemanticSearchService.cosineSimilarity(a, b), 1e-9);
        }

        @Test
        @DisplayName("opposite vectors score -1.0")
        void oppositeVectorsScoreNegativeOne() {
            float[] a = {1f, 0f};
            float[] b = {-1f, 0f};
            assertEquals(-1.0, SemanticSearchService.cosineSimilarity(a, b), 1e-9);
        }

        @Test
        @DisplayName("a zero-magnitude vector scores 0.0 instead of NaN")
        void zeroVectorScoresZero() {
            float[] a = {0f, 0f};
            float[] b = {1f, 1f};
            assertEquals(0.0, SemanticSearchService.cosineSimilarity(a, b), 1e-9);
        }

        @Test
        @DisplayName("a zero-magnitude second vector scores 0.0 instead of NaN")
        void zeroSecondVectorScoresZero() {
            float[] a = {1f, 1f};
            float[] b = {0f, 0f};
            assertEquals(0.0, SemanticSearchService.cosineSimilarity(a, b), 1e-9);
        }

        @Test
        @DisplayName("mismatched dimensions throw")
        void mismatchedDimensionsThrow() {
            float[] a = {1f, 2f};
            float[] b = {1f, 2f, 3f};
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> SemanticSearchService.cosineSimilarity(a, b));
        }
    }

    @Nested
    @DisplayName("generateAndStoreEmbedding")
    class GenerateAndStoreEmbedding {

        @Test
        @DisplayName("stores the embedding as JSON on success")
        void storesEmbeddingOnSuccess() {
            ContractDocument doc = fakeDoc(null);
            when(embeddingModel.embed("hello world")).thenReturn(new float[]{0.1f, 0.2f});

            semanticSearchService.generateAndStoreEmbedding(doc, "hello world");

            assertEquals("[0.1,0.2]", doc.getEmbedding());
            verify(documentRepository).save(doc);
            assertEquals(1, meterRegistry.get("bcm.embedding.generate").tag("outcome", "success").timer().count());
        }

        @Test
        @DisplayName("does nothing for blank text")
        void doesNothingForBlankText() {
            ContractDocument doc = fakeDoc(null);

            semanticSearchService.generateAndStoreEmbedding(doc, "   ");

            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not propagate failure when Ollama is unreachable")
        void doesNotPropagateEmbeddingFailure() {
            ContractDocument doc = fakeDoc(null);
            when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("connection refused"));

            semanticSearchService.generateAndStoreEmbedding(doc, "hello world");

            verify(documentRepository, never()).save(any());
            assertEquals(1, meterRegistry.get("bcm.embedding.generate").tag("outcome", "error").timer().count());
        }

        @Test
        @DisplayName("does nothing for null text")
        void doesNothingForNullText() {
            ContractDocument doc = fakeDoc(null);

            semanticSearchService.generateAndStoreEmbedding(doc, null);

            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("truncates text longer than the embedding model's input limit")
        void truncatesOverlyLongText() {
            ContractDocument doc = fakeDoc(null);
            String longText = "a".repeat(6100);
            String truncated = "a".repeat(6000);
            when(embeddingModel.embed(truncated)).thenReturn(new float[]{0.1f});

            semanticSearchService.generateAndStoreEmbedding(doc, longText);

            verify(embeddingModel).embed(truncated);
            verify(documentRepository).save(doc);
        }

        @Test
        @DisplayName("does not propagate a failure that carries no message")
        void doesNotPropagateFailureWithNullMessage() {
            ContractDocument doc = fakeDoc(null);
            when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException());

            semanticSearchService.generateAndStoreEmbedding(doc, "hello world");

            verify(documentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @AfterEach
        void clear() {
            TenantContext.clear();
        }

        @Test
        @DisplayName("returns empty list for a blank query")
        void blankQueryReturnsEmpty() {
            assertTrue(semanticSearchService.search("  ", 10).isEmpty());
        }

        @Test
        @DisplayName("returns empty list for a null query")
        void nullQueryReturnsEmpty() {
            assertTrue(semanticSearchService.search(null, 10).isEmpty());
        }

        @Test
        @DisplayName("returns empty list when the org has no indexed documents")
        void noCandidatesReturnsEmpty() {
            TenantContext.set(ORG_ID);
            when(documentRepository.findByOrgIdAndEmbeddingIsNotNull(ORG_ID)).thenReturn(List.of());

            assertTrue(semanticSearchService.search("penalty clause", 10).isEmpty());
        }

        @Test
        @DisplayName("ranks the closer document first")
        void ranksResultsByScore() throws Exception {
            TenantContext.set(ORG_ID);
            ContractDocument close = fakeDoc(objectMapper.writeValueAsString(new float[]{1f, 0f}));
            ContractDocument far = fakeDoc(objectMapper.writeValueAsString(new float[]{0f, 1f}));
            far.setId(11L);

            when(documentRepository.findByOrgIdAndEmbeddingIsNotNull(ORG_ID)).thenReturn(List.of(far, close));
            when(embeddingModel.embed("query")).thenReturn(new float[]{1f, 0f});

            List<SemanticSearchResultDTO> results = semanticSearchService.search("query", 10);

            assertEquals(2, results.size());
            assertEquals(10L, results.get(0).documentId());
            assertEquals(1.0, results.get(0).score(), 1e-9);
        }

        @Test
        @DisplayName("respects topK")
        void respectsTopK() throws Exception {
            TenantContext.set(ORG_ID);
            ContractDocument a = fakeDoc(objectMapper.writeValueAsString(new float[]{1f, 0f}));
            ContractDocument b = fakeDoc(objectMapper.writeValueAsString(new float[]{1f, 0f}));
            b.setId(11L);

            when(documentRepository.findByOrgIdAndEmbeddingIsNotNull(ORG_ID)).thenReturn(List.of(a, b));
            when(embeddingModel.embed("query")).thenReturn(new float[]{1f, 0f});

            assertEquals(1, semanticSearchService.search("query", 1).size());
        }

        @Test
        @DisplayName("skips a document with a corrupt embedding instead of failing the whole search")
        void skipsCorruptEmbedding() {
            TenantContext.set(ORG_ID);
            ContractDocument corrupt = fakeDoc("not valid json");

            when(documentRepository.findByOrgIdAndEmbeddingIsNotNull(ORG_ID)).thenReturn(List.of(corrupt));
            when(embeddingModel.embed("query")).thenReturn(new float[]{1f, 0f});

            assertTrue(semanticSearchService.search("query", 10).isEmpty());
        }
    }
}
