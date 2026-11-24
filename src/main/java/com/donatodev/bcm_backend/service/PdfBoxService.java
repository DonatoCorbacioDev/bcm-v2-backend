/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.UUID;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;
import com.donatodev.bcm_backend.util.Generated;

@Service
public class PdfBoxService {

    // Single quantified character class, no alternation: linear-time matching with
    // no backtracking ambiguity. extractAmount() filters out matches with no currency symbol.
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("[€$]?[\\d.,]++[€$]?", Pattern.CASE_INSENSITIVE);

    // Below this length, PDFTextStripper output is treated as "no text layer"
    // (a scanned/photographed page) rather than a genuinely short document.
    private static final int MIN_TEXT_LENGTH = 20;
    // Caps OCR cost on large scanned documents — Tesseract runs seconds per page.
    private static final int MAX_OCR_PAGES = 10;
    private static final float OCR_RENDER_DPI = 200f;
    // The page's size is the uploader's choice: a few-KB PDF with a huge page
    // would otherwise be drawn into hundreds of MB of the heap every tenant
    // shares (security audit 2026-11-24). An A4 page at 200 DPI is about 4 MP.
    static final long MAX_OCR_PIXELS = 20_000_000L;
    // Below this, a page shrunk to fit the budget is too blurred to read.
    static final float MIN_OCR_DPI = 50f;
    // At most this many OCRs drawing pages at once, whatever the requests.
    private static final int MAX_CONCURRENT_OCR = 2;
    private static final long OCR_SLOT_WAIT_SECONDS = 60;

    private static final Logger log = LoggerFactory.getLogger(PdfBoxService.class);

    private final OcrService ocrService;
    private final Semaphore ocrSlots = new Semaphore(MAX_CONCURRENT_OCR, true);

    public PdfBoxService(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    public DocumentAnalysisDTO analyzeDocument(UUID documentId, byte[] pdfBytes) {
        String rawText = extractRawText(pdfBytes);

        return new DocumentAnalysisDTO(
                documentId,
                rawText,
                extractField(rawText, "customer", "client", "counterparty", "cliente", "controparte"),
                extractField(rawText, "contract number", "numero contratto", "contract no", "n. contratto"),
                extractField(rawText, "start date", "data inizio", "data di inizio", "from"),
                extractField(rawText, "end date", "data fine", "data di fine", "scadenza", "to"),
                extractAmount(rawText));
    }

    public String extractRawText(byte[] pdfBytes) {
        try {
            return withDocument(pdfBytes, doc -> {
                String text = new PDFTextStripper().getText(doc);
                if (text.trim().length() >= MIN_TEXT_LENGTH) {
                    return text;
                }
                return extractTextViaOcr(doc);
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract text from PDF", e);
        }
    }

    // Isolated so @Generated only excludes the try-with-resources plumbing from coverage,
    // not the business logic in the caller's lambda: the compiler-duplicated catch handler
    // for a close() that fails after a successful body can't be hit without mocking PDFBox's
    // internal PDDocument construction, which would test the mock rather than real behavior.
    @Generated
    private <T> T withDocument(byte[] pdfBytes, PdfDocumentFunction<T> action) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return action.apply(doc);
        }
    }

    @FunctionalInterface
    private interface PdfDocumentFunction<T> {
        T apply(PDDocument doc) throws IOException;
    }

    private String extractTextViaOcr(PDDocument doc) throws IOException {
        if (!acquireOcrSlot()) {
            log.warn("OCR skipped: no free slot within {} s, the document is kept without extracted text",
                    OCR_SLOT_WAIT_SECONDS);
            return "";
        }
        try {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = Math.min(doc.getNumberOfPages(), MAX_OCR_PAGES);
            StringBuilder text = new StringBuilder();
            for (int page = 0; page < pageCount; page++) {
                float dpi = ocrDpiFor(doc.getPage(page));
                if (dpi < 0) {
                    log.warn("OCR skipped on page {}: too large to read within {} pixels", page + 1, MAX_OCR_PIXELS);
                    continue;
                }
                // Grey: Tesseract reads no colour, and a pixel takes 1 byte instead of 4.
                BufferedImage image = renderer.renderImageWithDPI(page, dpi, ImageType.GRAY);
                text.append(ocrService.extractText(image)).append('\n');
            }
            return text.toString().trim();
        } finally {
            ocrSlots.release();
        }
    }

    /**
     * The resolution a page is drawn at for OCR: 200 DPI, lowered so that the
     * image stays within {@link #MAX_OCR_PIXELS}, or -1 when that would take it
     * under {@link #MIN_OCR_DPI} (the page is skipped). The size is the crop box
     * times the page's UserUnit, an overestimate if the renderer ignores it.
     */
    static float ocrDpiFor(PDPage page) {
        PDRectangle box = page.getCropBox();
        double unit = page.getUserUnit();
        double areaSquareInches = (box.getWidth() * unit / 72.0) * (box.getHeight() * unit / 72.0);
        if (!(areaSquareInches > 0)) {
            return -1;
        }
        double dpi = Math.min(OCR_RENDER_DPI, Math.sqrt(MAX_OCR_PIXELS / areaSquareInches));
        return dpi < MIN_OCR_DPI ? -1 : (float) dpi;
    }

    private boolean acquireOcrSlot() {
        try {
            return ocrSlots.tryAcquire(OCR_SLOT_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    String extractField(String text, String... keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            int idx = lower.indexOf(keyword);
            if (idx >= 0) {
                int lineEnd = text.indexOf('\n', idx);
                String line = lineEnd > 0 ? text.substring(idx, lineEnd) : text.substring(idx);
                // Extract the value after the colon separator (e.g. "Customer: Acme Corp" → "Acme Corp")
                int colonIdx = line.indexOf(':');
                if (colonIdx >= 0) {
                    String value = line.substring(colonIdx + 1).trim();
                    if (!value.isEmpty()) return value;
                }
            }
        }
        return null;
    }

    String extractAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            if (match.indexOf('€') >= 0 || match.indexOf('$') >= 0) {
                return match.trim();
            }
        }
        return null;
    }
}
