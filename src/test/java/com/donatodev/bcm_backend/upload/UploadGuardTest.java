package com.donatodev.bcm_backend.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OWASP File Upload Cheat Sheet, denied by default: an uploaded file reaches
 * the application only through {@link UploadChecks}. A class that takes a
 * {@code MultipartFile} may ask it for its name and hand it to the checks; it
 * may not read the bytes itself, so a new upload endpoint that skips the
 * allowlist, the limits and the antivirus fails here.
 */
class UploadGuardTest {

    private static final Path MAIN = Path.of("src/main/java/com/donatodev/bcm_backend");
    private static final Path UPLOAD = MAIN.resolve("upload");

    /** What reads, or writes to disk, the content of a MultipartFile. */
    private static final Pattern READS_CONTENT = Pattern.compile(
            "\\.(getBytes|getInputStream|getResource|transferTo)\\(\\s*\\)?");

    /** Each class that takes an upload, and the UploadChecks method it must call. */
    private static final Map<String, String> UPLOAD_SERVICES = Map.of(
            "ContractDocumentService.java", "uploadChecks.pdf(file)",
            "ElectronicInvoiceService.java", "uploadChecks.fatturaPa(file)",
            "ContractImportService.java", "uploadChecks.xlsx(file)");

    @Test
    @DisplayName("no class outside upload/ reads the content of an uploaded file")
    void onlyUploadChecksReadsUploadedContent() throws IOException {
        Map<String, List<String>> offenders = new TreeMap<>();
        for (Path file : multipartUsers()) {
            if (file.startsWith(UPLOAD)) {
                continue;
            }
            List<String> lines = Files.readAllLines(file).stream()
                    .filter(line -> READS_CONTENT.matcher(line).find())
                    .filter(line -> line.contains("file.") || line.contains("multipart"))
                    .map(String::strip)
                    .toList();
            if (!lines.isEmpty()) {
                offenders.put(MAIN.relativize(file).toString(), lines);
            }
        }

        assertThat(offenders).as("read the file through UploadChecks instead").isEmpty();
    }

    @Test
    @DisplayName("every class that takes an upload is known, and hands it to its check")
    void everyUploadGoesThroughItsCheck() throws IOException {
        Map<String, Boolean> found = new TreeMap<>();
        for (Path file : multipartUsers()) {
            String name = file.getFileName().toString();
            if (file.startsWith(UPLOAD) || name.endsWith("Controller.java")) {
                continue;
            }
            found.put(name, UPLOAD_SERVICES.containsKey(name) && Files.readString(file).contains(UPLOAD_SERVICES.get(name)));
        }

        assertThat(found).as("a new upload: add its check to UploadChecks and to UPLOAD_SERVICES")
                .containsOnlyKeys(UPLOAD_SERVICES.keySet())
                .allSatisfy((name, checked) -> assertThat(checked).as(name).isTrue());
    }

    @Test
    @DisplayName("the guard itself sees the reads it forbids")
    void thePatternMatchesWhatItShould() {
        assertThat(READS_CONTENT.matcher("byte[] bytes = file.getBytes();").find()).isTrue();
        assertThat(READS_CONTENT.matcher("WorkbookFactory.create(file.getInputStream())").find()).isTrue();
        assertThat(READS_CONTENT.matcher("file.transferTo(target);").find()).isTrue();
        assertThat(READS_CONTENT.matcher("file.getOriginalFilename()").find()).isFalse();
    }

    private static List<Path> multipartUsers() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("MultipartFile");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .sorted()
                    .toList();
        }
    }
}
