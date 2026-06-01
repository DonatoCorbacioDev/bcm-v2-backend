package com.donatodev.bcm_backend.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.TextractResultDTO;

import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.S3Object;

@Service
public class TextractService {

    // Detects amounts like €1.500, $500, 1000€ — simplified to avoid ReDoS and regex complexity limits
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("[€$][\\d.,]+|[\\d.,]+[€$]", Pattern.CASE_INSENSITIVE);

    @Value("${aws.s3.bucket}")
    private String bucketName;

    private final TextractClient textractClient;

    public TextractService(TextractClient textractClient) {
        this.textractClient = textractClient;
    }

    public TextractResultDTO extractFromS3(Long documentId, String s3Key) {
        DetectDocumentTextResponse response = textractClient.detectDocumentText( //NOSONAR
                DetectDocumentTextRequest.builder()
                        .document(Document.builder() //NOSONAR
                                .s3Object(S3Object.builder()
                                        .bucket(bucketName)
                                        .name(s3Key)
                                        .build())
                                .build())
                        .build());

        String rawText = response.blocks().stream()
                .filter(b -> b.blockType() == BlockType.LINE)
                .map(software.amazon.awssdk.services.textract.model.Block::text)
                .collect(Collectors.joining("\n"));

        return new TextractResultDTO(
                documentId,
                rawText,
                extractField(rawText, "customer", "client", "counterparty", "cliente", "controparte"),
                extractField(rawText, "contract number", "numero contratto", "contract no", "n. contratto"),
                extractField(rawText, "start date", "data inizio", "data di inizio", "from"),
                extractField(rawText, "end date", "data fine", "data di fine", "scadenza", "to"),
                extractAmount(rawText));
    }

    private String extractField(String text, String... keywords) {
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

    private String extractAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        return matcher.find() ? matcher.group().trim() : null;
    }
}
