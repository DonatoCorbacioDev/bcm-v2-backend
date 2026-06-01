package com.donatodev.bcm_backend.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.dto.TextractResultDTO;

import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;

@ExtendWith(MockitoExtension.class)
class TextractServiceTest {

    @Mock private TextractClient textractClient;

    private TextractService textractService;

    @BeforeEach
    void setup() {
        textractService = new TextractService(textractClient);
        ReflectionTestUtils.setField(textractService, "bucketName", "test-bucket");
    }

    private Block lineBlock(String text) {
        return Block.builder().blockType(BlockType.LINE).text(text).build();
    }

    @Nested
    @DisplayName("Unit Test: TextractService")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class VerifyTextractService {

        @Test
        @Order(1)
        @DisplayName("extractFromS3 returns raw text and detected fields")
        void shouldExtractFieldsFromDocumentText() {
            DetectDocumentTextResponse response = DetectDocumentTextResponse.builder()
                    .blocks(List.of(
                            lineBlock("Customer: Acme Corporation"),
                            lineBlock("Contract Number: CTR-2025-001"),
                            lineBlock("Start Date: 01/01/2025"),
                            lineBlock("End Date: 31/12/2025"),
                            lineBlock("Total Value: €15,000")
                    ))
                    .build();

            when(textractClient.detectDocumentText(any(DetectDocumentTextRequest.class)))
                    .thenReturn(response);

            TextractResultDTO result = textractService.extractFromS3(1L, "contracts/1/1/doc.pdf");

            assertNotNull(result.rawText());
            assertEquals("Acme Corporation", result.detectedCustomerName());
            assertEquals("CTR-2025-001", result.detectedContractNumber());
            assertEquals("01/01/2025", result.detectedStartDate());
            assertEquals("31/12/2025", result.detectedEndDate());
            assertNotNull(result.detectedAmount());
        }

        @Test
        @Order(2)
        @DisplayName("extractFromS3 returns null fields when keywords are absent")
        void shouldReturnNullFieldsWhenKeywordsAbsent() {
            DetectDocumentTextResponse response = DetectDocumentTextResponse.builder()
                    .blocks(List.of(lineBlock("This is a generic document with no structured fields.")))
                    .build();

            when(textractClient.detectDocumentText(any(DetectDocumentTextRequest.class)))
                    .thenReturn(response);

            TextractResultDTO result = textractService.extractFromS3(2L, "contracts/1/2/doc.pdf");

            assertNull(result.detectedCustomerName());
            assertNull(result.detectedContractNumber());
            assertNull(result.detectedAmount());
        }

        @Test
        @Order(3)
        @DisplayName("extractFromS3 detects Italian keywords")
        void shouldDetectItalianKeywords() {
            DetectDocumentTextResponse response = DetectDocumentTextResponse.builder()
                    .blocks(List.of(
                            lineBlock("Cliente: Mario Rossi S.r.l."),
                            lineBlock("Data inizio: 01/03/2025"),
                            lineBlock("Scadenza: 28/02/2026")
                    ))
                    .build();

            when(textractClient.detectDocumentText(any(DetectDocumentTextRequest.class)))
                    .thenReturn(response);

            TextractResultDTO result = textractService.extractFromS3(3L, "contracts/1/3/doc.pdf");

            assertEquals("Mario Rossi S.r.l.", result.detectedCustomerName());
            assertEquals("01/03/2025", result.detectedStartDate());
            assertEquals("28/02/2026", result.detectedEndDate());
        }
    }
}
