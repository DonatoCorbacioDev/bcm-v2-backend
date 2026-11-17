package com.donatodev.bcm_backend.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.mockito.Mockito;

import com.donatodev.bcm_backend.security.SecurityEvents;
import com.donatodev.bcm_backend.upload.MalwareScanner;
import com.donatodev.bcm_backend.upload.UploadChecks;

/** Upload checks and files for tests that are not about the checks themselves. */
public final class TestUploads {

    private TestUploads() {
    }

    /** The real checks, with the antivirus turned off as in the test profile. */
    public static UploadChecks checks() {
        return new UploadChecks(new MalwareScanner(false, "localhost", 0, 1000), Mockito.mock(SecurityEvents.class));
    }

    /** A real one-page PDF, as PDFBox writes it. */
    public static byte[] pdf() {
        return pdf(document -> { });
    }

    /** A real one-page PDF, changed by {@code edit} before it is written. */
    public static byte[] pdf(Consumer<PDDocument> edit) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            edit.accept(document);
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
