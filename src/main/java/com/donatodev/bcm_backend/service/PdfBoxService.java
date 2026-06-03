package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;

@Service
public class PdfBoxService {

    // Possessive quantifier ++ prevents backtracking, eliminating ReDoS risk
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("[€$][\\d.,]++|[\\d.,]++[€$]", Pattern.CASE_INSENSITIVE);

    public DocumentAnalysisDTO analyzeDocument(Long documentId, byte[] pdfBytes) {
        String rawText;
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            rawText = new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract text from PDF", e);
        }

        return new DocumentAnalysisDTO(
                documentId,
                rawText,
                extractField(rawText, "customer", "client", "counterparty", "cliente", "controparte"),
                extractField(rawText, "contract number", "numero contratto", "contract no", "n. contratto"),
                extractField(rawText, "start date", "data inizio", "data di inizio", "from"),
                extractField(rawText, "end date", "data fine", "data di fine", "scadenza", "to"),
                extractAmount(rawText));
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
        return matcher.find() ? matcher.group().trim() : null;
    }
}
