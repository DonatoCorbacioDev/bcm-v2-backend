package com.donatodev.bcm_backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;

@ExtendWith(MockitoExtension.class)
class PdfBoxServiceTest {

    private final PdfBoxService pdfBoxService = new PdfBoxService();

    private static byte[] pdfWithFields;
    private static byte[] pdfNoFields;
    private static byte[] pdfItalianFields;
    private static byte[] pdfKeywordNoColon;
    private static byte[] pdfKeywordEmptyColon;

    @BeforeAll
    static void createTestPdfs() throws IOException {
        pdfWithFields = buildPdf(
                "Customer: Acme Corporation",
                "Contract Number: CTR-2025-001",
                "Start Date: 01/01/2025",
                "End Date: 31/12/2025",
                "Total: EUR15000");

        pdfNoFields = buildPdf("This is a generic document with no structured fields.");

        pdfItalianFields = buildPdf(
                "Cliente: Mario Rossi S.r.l.",
                "Data inizio: 01/03/2025",
                "Scadenza: 28/02/2026");

        pdfKeywordNoColon = buildPdf("customer without colon here");

        pdfKeywordEmptyColon = buildPdf("customer:");
    }

    private static byte[] buildPdf(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.setLeading(15f);
                cs.newLineAtOffset(50, 700);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: PdfBoxService")
    @SuppressWarnings("unused")
    class VerifyPdfBoxService {

        @Test
        @Order(1)
        @DisplayName("analyzeDocument: extracts all fields from structured PDF")
        void shouldExtractAllFields() {
            DocumentAnalysisDTO result = pdfBoxService.analyzeDocument(1L, pdfWithFields);

            assertNotNull(result.rawText());
            assertEquals("Acme Corporation", result.detectedCustomerName());
            assertEquals("CTR-2025-001", result.detectedContractNumber());
            assertEquals("01/01/2025", result.detectedStartDate());
            assertEquals("31/12/2025", result.detectedEndDate());
        }

        @Test
        @Order(2)
        @DisplayName("analyzeDocument: returns null fields when keywords absent")
        void shouldReturnNullFieldsWhenNoKeywords() {
            DocumentAnalysisDTO result = pdfBoxService.analyzeDocument(2L, pdfNoFields);

            assertNull(result.detectedCustomerName());
            assertNull(result.detectedContractNumber());
            assertNull(result.detectedAmount());
        }

        @Test
        @Order(3)
        @DisplayName("analyzeDocument: detects Italian keywords")
        void shouldDetectItalianKeywords() {
            DocumentAnalysisDTO result = pdfBoxService.analyzeDocument(3L, pdfItalianFields);

            assertEquals("Mario Rossi S.r.l.", result.detectedCustomerName());
            assertEquals("01/03/2025", result.detectedStartDate());
            assertEquals("28/02/2026", result.detectedEndDate());
        }

        @Test
        @Order(4)
        @DisplayName("analyzeDocument: keyword found but no colon — field returns null")
        void shouldReturnNullWhenKeywordHasNoColon() {
            DocumentAnalysisDTO result = pdfBoxService.analyzeDocument(4L, pdfKeywordNoColon);

            assertNull(result.detectedCustomerName());
        }

        @Test
        @Order(5)
        @DisplayName("analyzeDocument: keyword found, colon present but empty value — field returns null")
        void shouldReturnNullWhenValueAfterColonIsEmpty() {
            DocumentAnalysisDTO result = pdfBoxService.analyzeDocument(5L, pdfKeywordEmptyColon);

            assertNull(result.detectedCustomerName());
        }

        @Test
        @Order(6)
        @DisplayName("analyzeDocument: throws UncheckedIOException on invalid PDF bytes")
        void shouldThrowOnInvalidPdfBytes() {
            byte[] invalid = "not a pdf".getBytes();

            assertThrows(java.io.UncheckedIOException.class,
                    () -> pdfBoxService.analyzeDocument(6L, invalid));
        }
    }
}
