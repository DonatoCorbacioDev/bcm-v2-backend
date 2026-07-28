package com.donatodev.bcm_backend.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private static final String CRLF_REGEX = "[\r\n]";

    // Field initializers double as defaults for plain `new OcrService()`
    // construction (tests) — @Value only overwrites them when Spring manages
    // the bean.
    @Value("${app.ocr.timeout-seconds:30}")
    private long timeoutSeconds = 30;

    @Value("${app.ocr.tesseract-command:tesseract}")
    private String tesseractCommand = "tesseract";

    @Value("${app.ocr.tessdata-dir:}")
    private String tessdataDir = "";

    @Value("${app.ocr.language:ita}")
    private String language = "ita";

    public String extractText(BufferedImage image) {
        Path tempImage;
        try {
            tempImage = createSecureTempFile("ocr-page-", ".png");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temp file for OCR", e);
        }

        try {
            ImageIO.write(image, "png", tempImage.toFile());
            Path outputFile = createSecureTempFile("ocr-output-", ".txt");
            try {
                return runTesseract(tempImage.toFile(), outputFile);
            } finally {
                Files.deleteIfExists(outputFile);
            }
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

    // Rendered contract pages (and Tesseract's recognized text) can contain
    // sensitive business content, so temp files must not be world-readable on
    // shared/multi-tenant hosts. The FileAttribute form is the one
    // SonarJava's S5443 check recognizes as race-free (permissions applied
    // atomically at creation, not after).
    private static Path createSecureTempFile(String prefix, String suffix) throws IOException {
        return createSecureTempFile(prefix, suffix, FileSystems.getDefault());
    }

    // Package-private overload so tests can force the non-POSIX branch with a
    // fake FileSystem, without swapping the JVM's actual default filesystem
    // (that would also break the real Path.toFile() calls extractText() makes
    // on the temp files this method returns).
    static Path createSecureTempFile(String prefix, String suffix, FileSystem fileSystem) throws IOException {
        if (fileSystem.supportedFileAttributeViews().contains("posix")) {
            FileAttribute<Set<PosixFilePermission>> ownerOnly =
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
            return Files.createTempFile(prefix, suffix, ownerOnly);
        }
        return createTempFileWindowsDevFallback(prefix, suffix);
    }

    // Windows has no POSIX file attributes, so this can't be hardened the same
    // way — acceptable because it only ever runs on a developer's own local
    // machine. Production always runs Linux containers (see CLAUDE.md), where
    // the POSIX branch above is the one actually taken.
    @SuppressWarnings("java:S5443")
    private static Path createTempFileWindowsDevFallback(String prefix, String suffix) throws IOException {
        return Files.createTempFile(prefix, suffix);
    }

    // Output is redirected to a file rather than read from the process's
    // stdout pipe: reading a pipe blocks until the child closes it (i.e.
    // exits), which would defeat the timeout below entirely — a hung
    // Tesseract would block here forever, before waitFor ever got a chance to
    // apply the timeout and kill it.
    private String runTesseract(File imageFile, Path outputFile) throws IOException {
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

        Process process = new ProcessBuilder(command)
                .redirectOutput(outputFile.toFile())
                .redirectErrorStream(true)
                .start();

        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("OCR timed out after {}s", timeoutSeconds);
                return "";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "";
        }

        return Files.readString(outputFile, StandardCharsets.UTF_8).trim();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? null : message.replaceAll(CRLF_REGEX, "_");
    }
}
