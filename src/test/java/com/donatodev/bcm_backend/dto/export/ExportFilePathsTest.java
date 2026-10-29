package com.donatodev.bcm_backend.dto.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExportFilePathsTest {

    @ParameterizedTest
    @CsvSource({
            "Contratto 2026.pdf, Contratto 2026.pdf",
            "Fattura né_01-A.xml, Fattura né_01-A.xml",
            "C:\\Users\\a\\doc.pdf, C__Users_a_doc.pdf",
            "a/b.pdf, a_b.pdf",
            ".hidden, _hidden",
    })
    @DisplayName("Keeps ordinary names and replaces separators")
    void sanitizesNames(String original, String expected) {
        assertThat(ExportFilePaths.safeFileName(original)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../etc/passwd", "..\\..\\windows\\system.ini", "..", "/abs/path.pdf"})
    @DisplayName("No name can climb out of its folder when the archive is unzipped")
    void blocksZipSlip(String original) {
        String safe = ExportFilePaths.safeFileName(original);

        assertThat(safe).doesNotContain("/", "\\").doesNotStartWith(".");
        assertThat(Arrays.asList(("files/contracts/1/2-" + safe).split("/"))).isNotEmpty().doesNotContain("..");
    }

    @Test
    @DisplayName("A missing or empty name still gives a usable file name")
    void fallsBackForEmptyNames() {
        assertThat(ExportFilePaths.safeFileName(null)).isEqualTo("file");
        assertThat(ExportFilePaths.safeFileName("   ")).isEqualTo("file");
    }

    @Test
    @DisplayName("Overlong names are cut from the front, keeping the extension")
    void truncatesKeepingExtension() {
        String safe = ExportFilePaths.safeFileName("x".repeat(300) + ".pdf");

        assertThat(safe).hasSize(120).endsWith(".pdf");
    }
}
