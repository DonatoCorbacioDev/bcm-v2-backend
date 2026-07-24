package com.donatodev.bcm_backend.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Runs the system Tesseract binary on a rendered PDF page image. Used as a
 * fallback by {@link PdfBoxService} when a document has no extractable text
 * layer (a scanned/photographed contract, as opposed to a digitally
 * generated PDF).
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private static final long TIMEOUT_SECONDS = 30;
    private static final String CRLF_REGEX = "[\r\n]";

    // Field initializers double as defaults for plain `new OcrService()`
    // construction (tests) — @Value only overwrites them when Spring manages
    // the bean.
    @Value("${app.ocr.tesseract-command:tesseract}")
    private String tesseractCommand = "tesseract";

    @Value("${app.ocr.tessdata-dir:}")
    private String tessdataDir = "";

    @Value("${app.ocr.language:ita}")
    private String language = "ita";

    public String extractText(BufferedImage image) {
        Path tempImage;
        try {
            tempImage = createSecureTempFile();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temp file for OCR", e);
        }

        try {
            ImageIO.write(image, "png", tempImage.toFile());
            return runTesseract(tempImage.toFile());
        } catch (IOException e) {
            log.warn("OCR failed: {}", safeMessage(e));
            return "";
        } finally {
            try {
                Files.delete(tempImage);
            } catch (IOException e) {
                log.warn("Could not delete OCR temp file: {}", safeMessage(e));
                tempImage.toFile().deleteOnExit();
            }
        }
    }

    // Rendered contract pages can contain sensitive business content, so the
    // temp file must not be world-readable on shared/multi-tenant hosts.
    // setReadable/setWritable (rather than a POSIX-only FileAttribute) works
    // on every platform the app runs on.
    private static Path createSecureTempFile() throws IOException {
        File file = File.createTempFile("ocr-page-", ".png");
        boolean restricted = file.setReadable(false, false)
                && file.setReadable(true, true)
                && file.setWritable(false, false)
                && file.setWritable(true, true);
        if (!restricted) {
            log.warn("Could not fully restrict permissions on OCR temp file");
        }
        return file.toPath();
    }

    private String runTesseract(File imageFile) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(tesseractCommand);
        command.add(imageFile.getAbsolutePath());
        command.add("stdout");
        command.add("-l");
        command.add(language);
        if (tessdataDir != null && !tessdataDir.isBlank()) {
            command.add("--tessdata-dir");
            command.add(tessdataDir);
        }

        Process process = new ProcessBuilder(command).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        try {
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("OCR timed out after {}s", TIMEOUT_SECONDS);
                return "";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "";
        }

        return output.trim();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? null : message.replaceAll(CRLF_REGEX, "_");
    }
}
