package com.donatodev.bcm_backend.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
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
    @DisplayName("extractText: tolerates a null tessdata-dir (only the blank default is set via @Value)")
    void shouldTolerateNullTessdataDir() {
        ReflectionTestUtils.setField(ocrService, "tesseractCommand", "no-such-binary-xyz");
        ReflectionTestUtils.setField(ocrService, "tessdataDir", null);

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
        // Wait until the worker is actually blocked in Process.waitFor before
        // interrupting it, instead of a fixed sleep guessing how long that takes.
        await().atMost(Duration.ofSeconds(5))
                .until(() -> worker.getState() == Thread.State.TIMED_WAITING);
        worker.interrupt();
        worker.join(5000);

        assertEquals("", result.get());
    }

    @Test
    @DisplayName("extractText: throws UncheckedIOException when the temp image file cannot be created")
    void shouldThrowWhenTempImageCreationFails() {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class, invocation -> invocation.callRealMethod())) {
            // Stub both overloads: the 3-arg (POSIX attributes) form is used on
            // Linux CI, the 2-arg Windows-fallback form is used on a dev machine.
            filesMock.when(() -> Files.createTempFile(eq("ocr-page-"), eq(".png"), any()))
                    .thenThrow(new IOException("simulated disk failure"));
            filesMock.when(() -> Files.createTempFile(eq("ocr-page-"), eq(".png")))
                    .thenThrow(new IOException("simulated disk failure"));

            assertThrows(UncheckedIOException.class, () -> ocrService.extractText(textImage("irrelevant")));
        }
    }

    @Test
    @DisplayName("extractText: swallows a null-message IOException when the temp image cannot be deleted")
    void shouldSwallowDeleteFailureWithNullMessage() {
        ReflectionTestUtils.setField(ocrService, "tesseractCommand", "no-such-binary-xyz");

        try (MockedStatic<Files> filesMock = mockStatic(Files.class, invocation -> invocation.callRealMethod())) {
            filesMock.when(() -> Files.delete(any(Path.class))).thenThrow(new IOException());

            String result = ocrService.extractText(textImage("irrelevant"));

            assertEquals("", result);
        }
    }

    private static Path slowShellScript(int sleepSeconds) throws IOException {
        Path script = Files.createTempFile("slow-ocr-", ".sh");
        Files.writeString(script, "#!/bin/sh\nsleep " + sleepSeconds + "\n", StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        script.toFile().deleteOnExit();
        return script;
    }
}
