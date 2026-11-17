package com.donatodev.bcm_backend.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.donatodev.bcm_backend.exception.MalwareScanUnavailableException;
import com.donatodev.bcm_backend.exception.UnsafeFileException;
import com.donatodev.bcm_backend.security.SecurityEvents;
import com.donatodev.bcm_backend.support.TestUploads;

/** OWASP File Upload Cheat Sheet: allowlist, validation, size, malware scanning. */
class UploadChecksTest {

    private MalwareScanner scanner;
    private SecurityEvents securityEvents;
    private UploadChecks checks;

    @BeforeEach
    void setUp() {
        scanner = mock(MalwareScanner.class);
        when(scanner.scan(any())).thenReturn(Optional.empty());
        securityEvents = mock(SecurityEvents.class);
        checks = new UploadChecks(scanner, securityEvents);
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    @Nested
    class Pdf {

        @Test
        void aRealPdfPassesAndItsBytesComeBack() throws Exception {
            byte[] pdf = TestUploads.pdf();

            assertThat(checks.pdf(file("contratto.PDF", pdf))).isEqualTo(pdf);
            verify(scanner).scan(pdf);
            verifyNoInteractions(securityEvents);
        }

        @Test
        void theTypeTheBrowserDeclaresIsIgnored() throws Exception {
            byte[] pdf = TestUploads.pdf();
            MultipartFile declaredHtml = new MockMultipartFile("file", "contratto.pdf", "text/html", pdf);

            assertThat(checks.pdf(declaredHtml)).isEqualTo(pdf);
        }

        @ParameterizedTest
        @ValueSource(strings = {"contratto.exe", "contratto.pdf.exe", "contratto.html", "contratto", "contrattopdf"})
        void anotherExtensionIsRefusedBeforeReadingIt(String name) {
            assertThatThrownBy(() -> checks.pdf(file(name, TestUploads.pdf())))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Sono supportati solo file PDF");
            verifyNoInteractions(scanner);
        }

        @Test
        void noNameIsRefused() {
            assertThatThrownBy(() -> checks.pdf(file(null, TestUploads.pdf())))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Sono supportati solo file PDF");
        }

        @ParameterizedTest
        @ValueSource(strings = {"<html><script>alert(1)</script>", "%PDX-1.4", "%PDF1.4", "MZ\u0090\u0000", "%PDF"})
        void contentThatIsNotAPdfIsRefusedWhateverItIsCalled(String content) {
            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", content.getBytes(StandardCharsets.ISO_8859_1))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Sono supportati solo file PDF");
            verifyNoInteractions(scanner);
        }

        @Test
        void aPdfHeaderOnGarbageIsRefusedAfterTheScan() {
            byte[] garbage = "%PDF-1.7\nnot really a pdf".getBytes(StandardCharsets.US_ASCII);

            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", garbage)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Il file non è un PDF valido");
            verify(scanner).scan(garbage);
        }

        @Test
        void aPdfWithNoPagesIsRefused() {
            byte[] empty = save(new PDDocument());

            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", empty)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Il PDF non contiene pagine");
        }

        @Test
        void aPdfThatNeedsAPasswordToOpenIsRefused() {
            byte[] locked = TestUploads.pdf(document -> protect(document, "segreta"));

            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", locked)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("I PDF protetti da password non sono supportati");
        }

        @Test
        void aPdfOnlyRestrictedAgainstEditingOpensAndPasses() throws Exception {
            byte[] restricted = TestUploads.pdf(document -> protect(document, ""));

            assertThat(checks.pdf(file("contratto.pdf", restricted))).isEqualTo(restricted);
        }

        @Test
        void overTenMegabytesIsRefusedBeforeReadingIt() {
            byte[] huge = new byte[(int) UploadChecks.MAX_PDF_BYTES + 1];
            System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, huge, 0, 5);

            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", huge)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Il file supera il limite di 10 MB");
            verifyNoInteractions(scanner);
        }

        @Test
        void exactlyTenMegabytesIsNotRefusedForItsSize() {
            byte[] limit = new byte[(int) UploadChecks.MAX_PDF_BYTES];
            System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, limit, 0, 5);

            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", limit)))
                    .hasMessage("Il file non è un PDF valido");
        }

        @Test
        void aSizeDeclaredSmallerThanTheBytesIsNotBelieved() {
            byte[] huge = new byte[(int) UploadChecks.MAX_PDF_BYTES + 1];
            MultipartFile lying = new MockMultipartFile("file", "contratto.pdf", "application/pdf", huge) {
                @Override
                public long getSize() {
                    return 10;
                }
            };

            assertThatThrownBy(() -> checks.pdf(lying)).hasMessage("Il file supera il limite di 10 MB");
        }

        @Test
        void bytesEmptyDespiteADeclaredSizeAreRefused() {
            MultipartFile lying = new MockMultipartFile("file", "contratto.pdf", "application/pdf", new byte[0]) {
                @Override
                public boolean isEmpty() {
                    return false;
                }
            };

            assertThatThrownBy(() -> checks.pdf(lying)).hasMessage("Il file è vuoto");
        }

        @Test
        void anEmptyFileIsRefused() {
            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", new byte[0]))).hasMessage("Il file è vuoto");
            assertThatThrownBy(() -> checks.pdf(null)).hasMessage("Il file è vuoto");
        }
    }

    @Nested
    class PdfActiveContentIsRefused {

        @Test
        void javaScriptRunWhenTheDocumentOpens() {
            refused(document -> document.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert(1)")), "JS");
        }

        @Test
        void aLinkThatLaunchesAProgram() {
            refused(document -> link(document, new PDActionLaunch().getCOSObject()), "Launch");
        }

        @ParameterizedTest
        @ValueSource(strings = {"GoToR", "GoToE", "ImportData", "SubmitForm", "RichMediaExecute", "JavaScript"})
        void otherActionsThatReachOutsideTheDocument(String action) {
            COSDictionary dictionary = new COSDictionary();
            dictionary.setName(COSName.TYPE, "Action");
            dictionary.setName(COSName.S, action);
            refused(document -> link(document, dictionary), action);
        }

        @Test
        void javaScriptInAPageAdditionalAction() {
            COSDictionary additional = new COSDictionary();
            additional.setItem(COSName.O, new PDActionJavaScript("app.alert(1)"));
            refused(document -> document.getPage(0).getCOSObject().setItem(COSName.AA, additional), "JS");
        }

        @Test
        void documentLevelJavaScriptInTheNameTree() {
            COSDictionary names = new COSDictionary();
            names.setItem(COSName.JAVA_SCRIPT, new COSDictionary());
            refused(document -> document.getDocumentCatalog().getCOSObject().setItem(COSName.NAMES, names), "JavaScript");
        }

        @Test
        void anEmbeddedFile() {
            COSDictionary names = new COSDictionary();
            names.setItem(COSName.EMBEDDED_FILES, new COSDictionary());
            refused(document -> document.getDocumentCatalog().getCOSObject().setItem(COSName.NAMES, names), "EmbeddedFiles");
        }

        @Test
        void aFileSpecificationCarryingItsFile() {
            COSDictionary spec = new COSDictionary();
            spec.setName(COSName.TYPE, "Filespec");
            spec.setItem(COSName.EF, new COSDictionary());
            refused(document -> document.getPage(0).getCOSObject().setItem(COSName.getPDFName("X"), spec), "EF");
        }

        @Test
        void anEmbeddedFileStreamOnItsOwn() {
            COSDictionary stream = new COSDictionary();
            stream.setName(COSName.TYPE, "EmbeddedFile");
            refused(document -> document.getPage(0).getCOSObject().setItem(COSName.getPDFName("X"), stream), "EmbeddedFile");
        }

        @Test
        void aRichMediaAnnotation() {
            COSDictionary annotation = new COSDictionary();
            annotation.setName(COSName.SUBTYPE, "RichMedia");
            refused(document -> annots(document).add(annotation), "RichMedia");
        }

        @Test
        void anXfaForm() {
            refused(document -> {
                PDAcroForm form = new PDAcroForm(document);
                form.getCOSObject().setItem(COSName.XFA, new COSArray());
                document.getDocumentCatalog().setAcroForm(form);
            }, "XFA");
        }

        @Test
        void namesWrittenInHexEscapesAreSeenAsWhatTheyMean() {
            // "/J#61vaScript" is /JavaScript and "/J#53" is /JS: a byte search would miss both.
            String raw = "%PDF-1.4\n"
                    + "1 0 obj << /Type /Catalog /Pages 2 0 R /OpenAction 4 0 R >> endobj\n"
                    + "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
                    + "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >> endobj\n"
                    + "4 0 obj << /S /J#61vaScript /J#53 (app.alert\\(1\\)) >> endobj\n"
                    + "trailer << /Root 1 0 R /Size 5 >>\n%%EOF\n";

            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", raw.getBytes(StandardCharsets.US_ASCII))))
                    .isInstanceOf(UnsafeFileException.class);
            verify(securityEvents).record(eq(SecurityEvents.Type.UNSAFE_UPLOAD), eq("pdf active content: JS"));
        }

        @Test
        void aPdfWithTooManyObjectsToCheckIsRefused() {
            try (PDDocument document = new PDDocument()) {
                document.addPage(new PDPage());
                COSArray many = new COSArray();
                for (int i = 0; i <= PdfActiveContent.MAX_OBJECTS; i++) {
                    many.add(new COSString("x"));
                }
                document.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("X"), many);

                assertThat(PdfActiveContent.find(document)).contains("too many objects");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void refused(Consumer<PDDocument> edit, String found) {
            byte[] pdf = TestUploads.pdf(edit);

            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", pdf)))
                    .isInstanceOf(UnsafeFileException.class)
                    .hasMessageContaining("contenuti attivi");
            verify(securityEvents).record(SecurityEvents.Type.UNSAFE_UPLOAD, "pdf active content: " + found);
        }
    }

    @Nested
    class PdfOrdinaryContentPasses {

        @Test
        void aLinkToAWebPage() throws Exception {
            PDActionURI uri = new PDActionURI();
            uri.setURI("https://example.com");
            passes(document -> link(document, uri.getCOSObject()));
        }

        @Test
        void openingOnTheFirstPageFitted() throws Exception {
            passes(document -> {
                PDPageFitDestination destination = new PDPageFitDestination();
                destination.setPage(document.getPage(0));
                document.getDocumentCatalog().setOpenAction(destination);
            });
        }

        @Test
        void aGoToActionInsideTheDocument() throws Exception {
            COSDictionary goTo = new COSDictionary();
            goTo.setName(COSName.S, "GoTo");
            passes(document -> link(document, goTo));
        }

        @Test
        void aFormWithoutXfa() throws Exception {
            passes(document -> document.getDocumentCatalog().setAcroForm(new PDAcroForm(document)));
        }

        @Test
        void objectsThatReferToEachOtherInACycle() throws Exception {
            passes(document -> {
                COSDictionary a = new COSDictionary();
                COSDictionary b = new COSDictionary();
                a.setItem(COSName.getPDFName("Next"), b);
                b.setItem(COSName.getPDFName("Next"), a);
                document.getPage(0).getCOSObject().setItem(COSName.getPDFName("X"), a);
            });
        }

        private void passes(Consumer<PDDocument> edit) throws IOException {
            byte[] pdf = TestUploads.pdf(edit);

            assertThat(checks.pdf(file("contratto.pdf", pdf))).isEqualTo(pdf);
            verifyNoInteractions(securityEvents);
        }
    }

    @Nested
    class Malware {

        @Test
        void aFileTheAntivirusRecognisesIsRefusedAndRecorded() {
            when(scanner.scan(any())).thenReturn(Optional.of("Win.Test.EICAR_HDB-1"));

            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", TestUploads.pdf())))
                    .isInstanceOf(UnsafeFileException.class)
                    .hasMessage("Il file è stato rifiutato dall'antivirus");
            verify(securityEvents).record(SecurityEvents.Type.UNSAFE_UPLOAD, "pdf malware: Win.Test.EICAR_HDB-1");
        }

        @Test
        void theScanComesBeforeAnyParsing() {
            when(scanner.scan(any())).thenReturn(Optional.of("Pdf.Exploit.Test"));
            byte[] garbage = "%PDF-1.7\nnot really a pdf".getBytes(StandardCharsets.US_ASCII);

            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", garbage)))
                    .isInstanceOf(UnsafeFileException.class);
        }

        @Test
        void everyKindIsScanned() {
            when(scanner.scan(any())).thenReturn(Optional.of("Sig"));

            assertThatThrownBy(() -> checks.fatturaPa(file("fattura.xml", "<x/>".getBytes(StandardCharsets.UTF_8))))
                    .isInstanceOf(UnsafeFileException.class);
            assertThatThrownBy(() -> checks.xlsx(file("contratti.xlsx", xlsx()))).isInstanceOf(UnsafeFileException.class);
            verify(securityEvents).record(SecurityEvents.Type.UNSAFE_UPLOAD, "xml malware: Sig");
            verify(securityEvents).record(SecurityEvents.Type.UNSAFE_UPLOAD, "xlsx malware: Sig");
        }

        @Test
        void noAnswerFromTheAntivirusRefusesTheFileWithoutRecordingAnAttack() {
            when(scanner.scan(any())).thenThrow(new MalwareScanUnavailableException("down", null));

            assertThatThrownBy(() -> checks.pdf(file("contratto.pdf", TestUploads.pdf())))
                    .isInstanceOf(MalwareScanUnavailableException.class);
            verify(securityEvents, never()).record(any(), any());
        }
    }

    @Nested
    class FatturaPa {

        @ParameterizedTest
        @ValueSource(strings = {"<?xml version=\"1.0\"?><p:FatturaElettronica/>", "  \r\n\t<FatturaElettronica/>",
                "﻿<?xml version=\"1.0\"?><x/>"})
        void xmlPasses(String content) throws Exception {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

            assertThat(checks.fatturaPa(file("IT01234567890_00001.XML", bytes))).isEqualTo(bytes);
        }

        @ParameterizedTest
        @ValueSource(strings = {"not xml", "{\"json\":true}", "   ", "%PDF-1.4"})
        void contentThatIsNotXmlIsRefused(String content) {
            assertThatThrownBy(() -> checks.fatturaPa(file("fattura.xml", content.getBytes(StandardCharsets.UTF_8))))
                    .hasMessage("Sono supportati solo file XML");
            verifyNoInteractions(scanner);
        }

        @Test
        void xmlStartingTooFarIntoTheFileIsRefused() {
            byte[] late = (" ".repeat(1024) + "<x/>").getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> checks.fatturaPa(file("fattura.xml", late))).hasMessage("Sono supportati solo file XML");
        }

        @ParameterizedTest
        @ValueSource(strings = {"fattura.txt", "fattura.xml.p7m", "fattura.html", "fattura.svg"})
        void anotherExtensionIsRefused(String name) {
            assertThatThrownBy(() -> checks.fatturaPa(file(name, "<x/>".getBytes(StandardCharsets.UTF_8))))
                    .hasMessage("Sono supportati solo file XML");
        }

        @Test
        void overFiveMegabytesIsRefused() {
            byte[] huge = new byte[(int) UploadChecks.MAX_XML_BYTES + 1];
            huge[0] = '<';

            assertThatThrownBy(() -> checks.fatturaPa(file("fattura.xml", huge))).hasMessage("Il file supera il limite di 5 MB");
        }
    }

    @Nested
    class Xlsx {

        @Test
        void aRealWorkbookPasses() throws Exception {
            byte[] workbook = xlsx();

            assertThat(checks.xlsx(file("Contratti.XLSX", workbook))).isEqualTo(workbook);
        }

        @ParameterizedTest
        @ValueSource(strings = {"contratti.xls", "contratti.csv", "contratti.xlsm", "contratti.zip"})
        void anotherExtensionIsRefused(String name) {
            assertThatThrownBy(() -> checks.xlsx(file(name, xlsx()))).hasMessage("Sono supportati solo file .xlsx");
        }

        @Test
        void anOldBinaryXlsCalledXlsxIsRefused() {
            byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

            assertThatThrownBy(() -> checks.xlsx(file("contratti.xlsx", ole2))).hasMessage("Sono supportati solo file .xlsx");
            verifyNoInteractions(scanner);
        }

        @Test
        void overFiveMegabytesIsRefused() {
            byte[] huge = new byte[(int) UploadChecks.MAX_XLSX_BYTES + 1];

            assertThatThrownBy(() -> checks.xlsx(file("contratti.xlsx", huge))).hasMessage("Il file supera il limite di 5 MB");
        }

        @Test
        void aZipThatIsNotAWorkbookPassesTheCheapChecksAndIsLeftToTheImport() throws Exception {
            byte[] zip = zip("hello.txt", "hi".getBytes(StandardCharsets.US_ASCII));

            assertThat(checks.xlsx(file("contratti.xlsx", zip))).isEqualTo(zip);
        }
    }

    static byte[] xlsx() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Contratti").createRow(0).createCell(0).setCellValue("Contract Number");
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] zip(String entry, byte[] content) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content);
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void link(PDDocument document, COSDictionary action) {
        PDAnnotationLink link = new PDAnnotationLink();
        link.getCOSObject().setItem(COSName.A, action);
        annots(document).add(link.getCOSObject());
    }

    private static COSArray annots(PDDocument document) {
        COSDictionary page = document.getPage(0).getCOSObject();
        COSArray annots = page.getCOSArray(COSName.ANNOTS);
        if (annots == null) {
            annots = new COSArray();
            page.setItem(COSName.ANNOTS, annots);
        }
        return annots;
    }

    private static void protect(PDDocument document, String userPassword) {
        try {
            StandardProtectionPolicy policy = new StandardProtectionPolicy("proprietario", userPassword, new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] save(PDDocument document) {
        try (document; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
