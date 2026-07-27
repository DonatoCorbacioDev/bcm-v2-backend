package com.donatodev.bcm_backend.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;

@ExtendWith(MockitoExtension.class)
class PdfBoxServiceTest {

    @Mock
    private OcrService ocrService;

    @InjectMocks
    private PdfBoxService pdfBoxService;

    private static byte[] pdfWithFields;
    private static byte[] pdfNoFields;
    private static byte[] pdfItalianFields;
    private static byte[] pdfWithAmount;
    private static byte[] pdfWithEuroAmount;
    private static byte[] pdfKeywordLastLine;
    private static byte[] pdfKeywordNoColon;
    private static byte[] pdfKeywordEmptyColon;
    private static byte[] scannedSinglePagePdf;
    private static byte[] scannedTwelvePagePdf;

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

        pdfWithAmount = buildPdf("Invoice total: $5,000");

        pdfWithEuroAmount = buildPdf("Totale fattura: €3.500");

        pdfKeywordLastLine = buildPdf("customer: Last Line Corp");

        pdfKeywordNoColon = buildPdf("customer without colon here");

        pdfKeywordEmptyColon = buildPdf("customer:");

        scannedSinglePagePdf = buildImageOnlyPdf(1);
        scannedTwelvePagePdf = buildImageOnlyPdf(12);
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

    /** Builds a PDF with pages containing only a rasterized image, no text operators — simulates a scanned document. */
    private static byte[] buildImageOnlyPdf(int pageCount) throws IOException {
        BufferedImage image = new BufferedImage(200, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, image);
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(pdImage, 50, 500, 200, 60);
                }
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
        @DisplayName("analyzeDocument: detects amount with $ symbol")
        void shouldDetectAmount() {
            DocumentAnalysisDTO result = pdfBoxService.analyzeDocument(6L, pdfWithAmount);

            assertNotNull(result.detectedAmount());
            assertTrue(result.detectedAmount().startsWith("$"));
        }

        @Test
        @Order(7)
        @DisplayName("analyzeDocument: extracts field when keyword is on last line (no trailing newline)")
        void shouldExtractFieldOnLastLine() {
            DocumentAnalysisDTO result = pdfBoxService.analyzeDocument(7L, pdfKeywordLastLine);

            assertEquals("Last Line Corp", result.detectedCustomerName());
        }

        @Test
        @Order(8)
        @DisplayName("extractField: returns value when text has no trailing newline (lineEnd == -1 branch)")
        void shouldExtractFieldWithNoNewline() {
            String result = pdfBoxService.extractField("customer: Acme Corp", "customer");
            assertEquals("Acme Corp", result);
        }

        @Test
        @Order(15)
        @DisplayName("extractField: colon present but value empty — falls through to the next keyword instead of returning")
        void shouldFallThroughWhenValueAfterColonIsEmpty() {
            String result = pdfBoxService.extractField("customer:", "customer");
            assertNull(result);
        }

        @Test
        @Order(9)
        @DisplayName("analyzeDocument: throws UncheckedIOException on invalid PDF bytes")
        void shouldThrowOnInvalidPdfBytes() {
            byte[] invalid = "not a pdf".getBytes();

            assertThrows(java.io.UncheckedIOException.class,
                    () -> pdfBoxService.analyzeDocument(6L, invalid));
        }

        @Test
        @Order(10)
        @DisplayName("analyzeDocument: detects amount with € symbol")
        void shouldDetectEuroAmount() {
            DocumentAnalysisDTO result = pdfBoxService.analyzeDocument(8L, pdfWithEuroAmount);

            assertNotNull(result.detectedAmount());
            assertTrue(result.detectedAmount().startsWith("€"));
        }

        @Test
        @Order(11)
        @DisplayName("extractRawText: does not invoke OCR when the PDF already has a text layer")
        void shouldNotInvokeOcrWhenTextLayerSufficient() {
            pdfBoxService.extractRawText(pdfWithFields);

            verify(ocrService, never()).extractText(any());
        }

        @Test
        @Order(12)
        @DisplayName("extractRawText: falls back to OCR when the PDF has no text layer")
        void shouldFallBackToOcrWhenTextLayerEmpty() {
            when(ocrService.extractText(any())).thenReturn("Cliente: OCR Corp");

            String result = pdfBoxService.extractRawText(scannedSinglePagePdf);

            assertEquals("Cliente: OCR Corp", result);
            verify(ocrService, times(1)).extractText(any());
        }

        @Test
        @Order(13)
        @DisplayName("analyzeDocument: extracts fields from OCR text when the PDF is scanned")
        void shouldExtractFieldsFromOcrText() {
            when(ocrService.extractText(any())).thenReturn("Cliente: Scansione Corp");

            DocumentAnalysisDTO result = pdfBoxService.analyzeDocument(9L, scannedSinglePagePdf);

            assertEquals("Scansione Corp", result.detectedCustomerName());
        }

        @Test
        @Order(14)
        @DisplayName("extractRawText: caps OCR to at most 10 pages on large scanned documents")
        void shouldCapOcrToMaxPages() {
            when(ocrService.extractText(any())).thenReturn("page text");

            pdfBoxService.extractRawText(scannedTwelvePagePdf);

            verify(ocrService, times(10)).extractText(any());
        }
    }
}
