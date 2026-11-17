package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.donatodev.bcm_backend.exception.UnsafeFileException;
import com.donatodev.bcm_backend.security.SecurityEvents;
import com.donatodev.bcm_backend.support.TestUploads;
import com.donatodev.bcm_backend.upload.MalwareScanner;
import com.donatodev.bcm_backend.upload.UploadChecks;

/**
 * The antivirus client against a real ClamAV, the image production runs
 * (OWASP File Upload Cheat Sheet, malware scanning). The image ships with its
 * signatures; freshclam is off so the test needs no download at startup.
 * <p>
 * The test file is EICAR, the industry's harmless stand-in for a virus that
 * every antivirus reports. It is assembled in memory from its reverse, so the
 * source file itself is not flagged by the developer machine's antivirus.
 */
@DisplayName("Integration Test: uploads scanned by a real ClamAV")
class ClamAvScannerIT {

    /** Same image as the clamav service in bcm-v2-docker (the debian variant has the arm64 build). */
    static final String IMAGE = "clamav/clamav:1.5.4-debian";

    private static final byte[] EICAR = new StringBuilder(
            "*H+H$!ELIF-TSET-SURIVITNA-DRADNATS-RACIE$}7)CC7)^P(45XZP\\4[PA@%P!O5X")
            .reverse().toString().getBytes(StandardCharsets.US_ASCII);

    private static GenericContainer<?> clamav;
    private static MalwareScanner scanner;

    @BeforeAll
    @SuppressWarnings("resource") // closed in stop()
    static void start() {
        clamav = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withEnv("CLAMAV_NO_FRESHCLAMD", "true")
                .withExposedPorts(3310)
                // clamd opens its port only once the signatures are loaded: a minute or two.
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
        clamav.start();
        scanner = new MalwareScanner(true, clamav.getHost(), clamav.getMappedPort(3310), 60_000);
    }

    @AfterAll
    static void stop() {
        if (clamav != null) {
            clamav.stop();
        }
    }

    @Test
    void theTestVirusIsRecognised() {
        assertThat(EICAR).hasSize(68);

        Optional<String> signature = scanner.scan(EICAR);

        assertThat(signature).isPresent();
        assertThat(signature.get()).containsIgnoringCase("eicar");
    }

    @Test
    void anOrdinaryPdfIsClean() {
        assertThat(scanner.scan(TestUploads.pdf())).isEmpty();
    }

    @Test
    void aLargeFileGoesThroughInChunks() {
        byte[] large = new byte[9 * 1024 * 1024];
        java.util.Arrays.fill(large, (byte) 'a');

        assertThat(scanner.scan(large)).isEmpty();
    }

    @Test
    void theTestVirusInsideASpreadsheetIsRefusedAndRecorded() {
        SecurityEvents events = Mockito.mock(SecurityEvents.class);
        UploadChecks checks = new UploadChecks(scanner, events);
        MockMultipartFile spreadsheet = new MockMultipartFile("file", "contratti.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", zipOf(EICAR));

        assertThatThrownBy(() -> checks.xlsx(spreadsheet))
                .isInstanceOf(UnsafeFileException.class)
                .hasMessage("Il file è stato rifiutato dall'antivirus");
        Mockito.verify(events).record(Mockito.eq(SecurityEvents.Type.UNSAFE_UPLOAD), Mockito.startsWith("xlsx malware: "));
    }

    /** EICAR inside a ZIP, which ClamAV opens and scans: how it would arrive as an .xlsx. */
    private static byte[] zipOf(byte[] content) {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(content);
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
