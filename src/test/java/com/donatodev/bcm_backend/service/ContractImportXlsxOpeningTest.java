package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** OWASP File Upload Cheat Sheet: the import opens a real .xlsx and nothing else, zip bombs included. */
class ContractImportXlsxOpeningTest {

    @Test
    void aRealWorkbookOpens() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Contratti");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        try (Workbook opened = ContractImportService.openXlsx(bytes)) {
            assertThat(opened).isInstanceOf(XSSFWorkbook.class);
            assertThat(opened.getSheetName(0)).isEqualTo("Contratti");
        }
    }

    @Test
    void theOldBinaryFormatIsNotOpenedEvenIfReadable() throws Exception {
        byte[] xls;
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Contratti");
            workbook.write(out);
            xls = out.toByteArray();
        }

        assertThatThrownBy(() -> ContractImportService.openXlsx(xls))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Il file non è un file Excel .xlsx valido");
    }

    @Test
    void aZipThatIsNotAWorkbookIsTheCallersInput() {
        byte[] zip = zip("[Content_Types].xml", "not a workbook".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> ContractImportService.openXlsx(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Il file non è un file Excel .xlsx valido");
    }

    @Test
    void aZipBombIsRefused() throws IOException {
        // 15 MB of zeros compress about a thousand times: far past the 1:100 ratio allowed,
        // while under the whole file's 20 MB, so POI's own ratio check is what refuses it.
        // A real workbook carrying one extra part that expands from a few KB.
        byte[] bomb = workbookWithExtraPart("xl/media/padding.bin", new byte[15 * 1024 * 1024]);
        assertThat((long) bomb.length).isLessThan(UploadChecksLimit.XLSX);

        assertThatThrownBy(() -> ContractImportService.openXlsx(bomb))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Il file non è un file Excel .xlsx valido")
                .rootCause().hasMessageContaining("Zip bomb");
    }

    @Test
    void aWorkbookExpandingPastTheWholeFileLimitIsRefusedBeforePoiReadsIt() throws IOException {
        // Two parts of 12 MB each: each one within POI's per-entry limit, 24 MB together.
        byte[] wide = workbookWithExtraParts(new byte[12 * 1024 * 1024], new byte[12 * 1024 * 1024]);
        assertThat((long) wide.length).isLessThan(UploadChecksLimit.XLSX);

        assertThatThrownBy(() -> ContractImportService.openXlsx(wide))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ContractImportService.TOO_LARGE_EXPANDED)
                .hasNoCause();
    }

    @Test
    void aWorkbookWithTooManyPartsIsRefused() throws IOException {
        byte[][] parts = new byte[ContractImportService.MAX_ENTRIES][];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = new byte[1];
        }
        byte[] many = workbookWithExtraParts(parts);

        assertThatThrownBy(() -> ContractImportService.openXlsx(many))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ContractImportService.TOO_MANY_PARTS);
    }

    @Test
    void zipBombLimitsAreSetTighterThanPoisDefaults() {
        assertThat(ZipSecureFile.getMinInflateRatio()).isEqualTo(ContractImportService.MIN_INFLATE_RATIO);
        assertThat(ZipSecureFile.getMaxEntrySize()).isEqualTo(ContractImportService.MAX_ENTRY_BYTES)
                .isLessThan(0xFFFFFFFFL);
    }

    private static final class UploadChecksLimit {
        static final long XLSX = com.donatodev.bcm_backend.upload.UploadChecks.MAX_XLSX_BYTES;
    }

    private static byte[] workbookWithExtraParts(byte[]... contents) throws IOException {
        byte[] bytes = workbookWithExtraPart("xl/media/extra-0.bin", contents[0]);
        for (int i = 1; i < contents.length; i++) {
            bytes = withExtraEntry(bytes, "xl/media/extra-" + i + ".bin", contents[i]);
        }
        return bytes;
    }

    private static byte[] withExtraEntry(byte[] zipBytes, String extra, byte[] content) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out);
             java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                in.transferTo(zip);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(extra));
            zip.write(content);
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        }
    }

    private static byte[] workbookWithExtraPart(String extra, byte[] content) throws IOException {
        byte[] real;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Contratti");
            workbook.write(out);
            real = out.toByteArray();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out);
             java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(real))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                in.transferTo(zip);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(extra));
            zip.write(content);
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        }
    }

    private static byte[] zip(String entry, byte[] content) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content);
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
