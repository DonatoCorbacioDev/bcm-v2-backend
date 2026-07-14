package com.donatodev.bcm_backend.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OcrServiceTest {

    private final OcrService ocrService = new OcrService();

    private static BufferedImage textImage(String text) {
        BufferedImage image = new BufferedImage(500, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("Monospaced", Font.PLAIN, 20));
        g.drawString(text, 10, 40);
        g.dispose();
        return image;
    }

    @Test
    @DisplayName("extractText: runs Tesseract and returns the recognized text")
    void shouldExtractTextFromImage() {
        String result = ocrService.extractText(textImage("Contratto"));

        assertTrue(result.toLowerCase().contains("contratto"),
                "expected OCR output to contain 'contratto', got: " + result);
    }

    @Test
    @DisplayName("extractText: returns empty string when the Tesseract binary is missing")
    void shouldReturnEmptyWhenBinaryMissing() {
        ReflectionTestUtils.setField(ocrService, "tesseractCommand", "no-such-binary-xyz");

        String result = ocrService.extractText(textImage("irrelevant"));

        assertEquals("", result);
    }
}
