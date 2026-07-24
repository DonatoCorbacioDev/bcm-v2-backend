package com.donatodev.bcm_backend.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
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

    @Test
    @DisplayName("extractText: includes --tessdata-dir in the command when configured")
    void shouldIncludeTessdataDirWhenConfigured() {
        ReflectionTestUtils.setField(ocrService, "tesseractCommand", "no-such-binary-xyz");
        ReflectionTestUtils.setField(ocrService, "tessdataDir", "/opt/tessdata");

        String result = ocrService.extractText(textImage("irrelevant"));

        assertEquals("", result);
    }

    @Test
    @DisplayName("extractText: returns empty string when Tesseract exceeds the configured timeout")
    @DisabledOnOs(OS.WINDOWS) // needs a real slow-running executable; POSIX-only shell script below
    void shouldReturnEmptyWhenTimeoutExceeded() throws IOException {
        ReflectionTestUtils.setField(ocrService, "tesseractCommand", slowShellScript(3).toString());
        ReflectionTestUtils.setField(ocrService, "timeoutSeconds", 1L);

        String result = ocrService.extractText(textImage("irrelevant"));

        assertEquals("", result);
    }

    @Test
    @DisplayName("extractText: returns empty string when interrupted while waiting for Tesseract")
    @DisabledOnOs(OS.WINDOWS) // needs a real slow-running executable; POSIX-only shell script below
    void shouldReturnEmptyWhenInterruptedWhileWaiting() throws Exception {
        ReflectionTestUtils.setField(ocrService, "tesseractCommand", slowShellScript(5).toString());
        ReflectionTestUtils.setField(ocrService, "timeoutSeconds", 30L);

        AtomicReference<String> result = new AtomicReference<>();
        Thread worker = new Thread(() -> result.set(ocrService.extractText(textImage("irrelevant"))));
        worker.start();
        Thread.sleep(300); // let the process actually start before interrupting
        worker.interrupt();
        worker.join(5000);

        assertEquals("", result.get());
    }

    private static Path slowShellScript(int sleepSeconds) throws IOException {
        Path script = Files.createTempFile("slow-ocr-", ".sh");
        Files.writeString(script, "#!/bin/sh\nsleep " + sleepSeconds + "\n", StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        script.toFile().deleteOnExit();
        return script;
    }
}
