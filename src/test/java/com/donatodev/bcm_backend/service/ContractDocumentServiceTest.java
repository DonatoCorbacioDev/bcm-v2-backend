package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.dto.ContractDocumentDTO;
import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;
import com.donatodev.bcm_backend.dto.DocumentDiffDTO;
import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ContractDocumentServiceTest {

    @Mock private ContractDocumentRepository documentRepository;
    @Mock private ContractAccessGuard contractAccessGuard;
    @Mock private LocalStorageService localStorageService;
    @Mock private PdfBoxService pdfBoxService;
    @Mock private MlProxyService mlProxyService;
    @Mock private SemanticSearchService semanticSearchService;

    @InjectMocks
    private ContractDocumentService contractDocumentService;

    private static final byte[] VALID_PDF = "%PDF-1.4 test".getBytes();
    private static final long CONTRACT_ID = 1L;
    private static final long DOC_ID = 10L;
    private static final String BACKEND_URL = "http://localhost:8090/api/v1";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(contractDocumentService, "backendBaseUrl", BACKEND_URL);
    }

    private Contracts fakeContract() {
        Contracts c = new Contracts();
        c.setId(CONTRACT_ID);
        return c;
    }

    private ContractDocument fakeDoc(Contracts contract) {
        ContractDocument doc = new ContractDocument();
        doc.setId(DOC_ID);
        doc.setContract(contract);
        doc.setStoragePath("contracts/0/1/uuid-contract.pdf");
        doc.setFileName("contract.pdf");
        doc.setFileSize((long) VALID_PDF.length);
        doc.setContentType("application/pdf");
        doc.setUploadedAt(Instant.parse("2027-01-15T12:00:00Z"));
        doc.setVersionGroupId(DOC_ID);
        doc.setVersionNumber(1);
        return doc;
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: ContractDocumentService")
    @SuppressWarnings("unused")
    class VerifyContractDocumentService {

        // ---- uploadDocument ----

        @Test
        @Order(1)
        @DisplayName("uploadDocument: happy path returns DTO with download URL")
        void shouldUploadDocumentSuccessfully() throws IOException {
            Contracts contract = fakeContract();
            ContractDocument saved = fakeDoc(contract);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(localStorageService.storeDocument(any(), eq(CONTRACT_ID), any()))
                    .thenReturn("contracts/0/1/uuid-contract.pdf");
            when(documentRepository.save(any(ContractDocument.class))).thenReturn(saved);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);

            ContractDocumentDTO result = contractDocumentService.uploadDocument(CONTRACT_ID, file);

            assertNotNull(result);
            assertEquals("contract.pdf", result.fileName());
            assertTrue(result.downloadUrl().contains("/contracts/" + CONTRACT_ID + "/documents/" + DOC_ID + "/download"));
        }

        @Test
        @Order(2)
        @DisplayName("uploadDocument: throws ContractNotFoundException when contract missing")
        void shouldThrowWhenContractNotFound() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID))
                    .thenThrow(new ContractNotFoundException("Contract ID " + CONTRACT_ID + " not found"));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(3)
        @DisplayName("uploadDocument: throws IllegalArgumentException on empty file")
        void shouldThrowOnEmptyFile() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.pdf", "application/pdf", new byte[0]);

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(4)
        @DisplayName("uploadDocument: throws IllegalArgumentException when file too large")
        void shouldThrowWhenFileTooLarge() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());

            byte[] huge = new byte[11 * 1024 * 1024];
            huge[0] = '%'; huge[1] = 'P'; huge[2] = 'D'; huge[3] = 'F';
            MockMultipartFile file = new MockMultipartFile(
                    "file", "huge.pdf", "application/pdf", huge);

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(5)
        @DisplayName("uploadDocument: throws IllegalArgumentException when not a PDF (magic bytes)")
        void shouldThrowOnNonPdfFile() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "image.png", "image/png", "not a pdf content".getBytes());

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(6)
        @DisplayName("uploadDocument: throws IllegalArgumentException when file shorter than 4 bytes")
        void shouldThrowOnTooShortFile() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "tiny.pdf", "application/pdf", new byte[]{'%', 'P'});

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(6)
        @DisplayName("uploadDocument: throws when byte[1] is wrong (not 'P')")
        void shouldThrowWhenSecondByteMismatch() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "file.pdf", "application/pdf", "%XDF test".getBytes());

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(7)
        @DisplayName("uploadDocument: throws when byte[2] is wrong (not 'D')")
        void shouldThrowWhenThirdByteMismatch() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "file.pdf", "application/pdf", "%PXF test".getBytes());

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(8)
        @DisplayName("uploadDocument: throws when byte[3] is wrong (not 'F')")
        void shouldThrowWhenFourthByteMismatch() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "file.pdf", "application/pdf", "%PDX test".getBytes());

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        // ---- getDocuments ----

        @Test
        @Order(9)
        @DisplayName("getDocuments: returns mapped DTOs with download URLs")
        void shouldReturnDocumentList() {
            Contracts contract = fakeContract();
            ContractDocument doc = fakeDoc(contract);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of(doc));

            List<ContractDocumentDTO> result = contractDocumentService.getDocuments(CONTRACT_ID);

            assertEquals(1, result.size());
            assertEquals(DOC_ID, result.get(0).id());
            assertTrue(result.get(0).downloadUrl().contains("/download"));
        }

        @Test
        @Order(10)
        @DisplayName("getDocuments: returns empty list when no documents")
        void shouldReturnEmptyList() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(documentRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of());

            List<ContractDocumentDTO> result = contractDocumentService.getDocuments(CONTRACT_ID);

            assertEquals(0, result.size());
        }

        // ---- extractText ----

        @Test
        @Order(11)
        @DisplayName("extractText: delegates to PdfBoxService and returns result")
        void shouldExtractText() {
            Contracts contract = fakeContract();
            ContractDocument doc = fakeDoc(contract);
            DocumentAnalysisDTO expected = new DocumentAnalysisDTO(
                    DOC_ID, "raw text", "Acme", null, null, null, null);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(doc));
            when(localStorageService.readDocument(doc.getStoragePath())).thenReturn(VALID_PDF);
            when(pdfBoxService.analyzeDocument(DOC_ID, VALID_PDF)).thenReturn(expected);

            DocumentAnalysisDTO result = contractDocumentService.extractText(CONTRACT_ID, DOC_ID);

            assertEquals("Acme", result.detectedCustomerName());
        }

        @Test
        @Order(12)
        @DisplayName("extractText: throws ContractNotFoundException when document missing")
        void shouldThrowWhenDocumentNotFoundOnExtract() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.extractText(CONTRACT_ID, DOC_ID));
        }

        // ---- downloadDocument ----

        @Test
        @Order(13)
        @DisplayName("downloadDocument: returns bytes and metadata")
        void shouldDownloadDocument() {
            Contracts contract = fakeContract();
            ContractDocument doc = fakeDoc(contract);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(doc));
            when(localStorageService.readDocument(doc.getStoragePath())).thenReturn(VALID_PDF);

            FileDownload result = contractDocumentService.downloadDocument(CONTRACT_ID, DOC_ID);

            assertNotNull(result.bytes());
            assertEquals("contract.pdf", result.fileName());
            assertEquals("application/pdf", result.contentType());
        }

        @Test
        @Order(14)
        @DisplayName("downloadDocument: throws ContractNotFoundException when document missing")
        void shouldThrowWhenDocumentNotFoundOnDownload() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.downloadDocument(CONTRACT_ID, DOC_ID));
        }

        // ---- deleteDocument ----

        @Test
        @Order(15)
        @DisplayName("deleteDocument: deletes from local storage and repository")
        void shouldDeleteDocument() {
            Contracts contract = fakeContract();
            ContractDocument doc = fakeDoc(contract);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(doc));

            contractDocumentService.deleteDocument(CONTRACT_ID, DOC_ID);

            verify(localStorageService).deleteDocument(doc.getStoragePath());
            verify(documentRepository).delete(doc);
        }

        @Test
        @Order(16)
        @DisplayName("deleteDocument: throws ContractNotFoundException when document missing")
        void shouldThrowWhenDocumentNotFoundOnDelete() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.deleteDocument(CONTRACT_ID, DOC_ID));
        }

        // ---- FileDownload record ----

        @Test
        @Order(17)
        @DisplayName("FileDownload: equals same reference returns true")
        void documentDownloadEqualsSameReference() {
            FileDownload dd = new FileDownload(VALID_PDF, "f.pdf", "application/pdf");
            assertEquals(dd, dd);
        }

        @Test
        @Order(18)
        @DisplayName("FileDownload: equals null returns false")
        void documentDownloadEqualsNull() {
            FileDownload dd = new FileDownload(VALID_PDF, "f.pdf", "application/pdf");
            boolean result = dd.equals(null);
            assertFalse(result);
        }

        @Test
        @Order(19)
        @DisplayName("FileDownload: equals different type returns false")
        void documentDownloadEqualsDifferentType() {
            FileDownload dd = new FileDownload(VALID_PDF, "f.pdf", "application/pdf");
            boolean result = dd.equals("string");
            assertFalse(result);
        }

        @Test
        @Order(20)
        @DisplayName("FileDownload: equals same content returns true, hashCode matches")
        void documentDownloadEqualsAndHashCode() {
            FileDownload dd1 = new FileDownload(VALID_PDF, "f.pdf", "application/pdf");
            FileDownload dd2 = new FileDownload(VALID_PDF, "f.pdf", "application/pdf");
            assertEquals(dd1, dd2);
            assertEquals(dd2.hashCode(), dd1.hashCode());
        }

        @Test
        @Order(22)
        @DisplayName("FileDownload: equals returns false when bytes differ")
        void documentDownloadNotEqualsDifferentBytes() {
            FileDownload dd1 = new FileDownload(VALID_PDF, "f.pdf", "application/pdf");
            FileDownload dd2 = new FileDownload(new byte[]{1, 2, 3}, "f.pdf", "application/pdf");
            assertNotEquals(dd1, dd2);
        }

        @Test
        @Order(23)
        @DisplayName("FileDownload: equals returns false when fileName differs")
        void documentDownloadNotEqualsDifferentFileName() {
            FileDownload dd1 = new FileDownload(VALID_PDF, "a.pdf", "application/pdf");
            FileDownload dd2 = new FileDownload(VALID_PDF, "b.pdf", "application/pdf");
            assertNotEquals(dd1, dd2);
        }

        @Test
        @Order(24)
        @DisplayName("FileDownload: equals returns false when contentType differs")
        void documentDownloadNotEqualsDifferentContentType() {
            FileDownload dd1 = new FileDownload(VALID_PDF, "f.pdf", "application/pdf");
            FileDownload dd2 = new FileDownload(VALID_PDF, "f.pdf", "text/plain");
            assertNotEquals(dd1, dd2);
        }

        @Test
        @Order(21)
        @DisplayName("FileDownload: toString contains fileName")
        void documentDownloadToString() {
            FileDownload dd = new FileDownload(VALID_PDF, "contract.pdf", "application/pdf");
            assertTrue(dd.toString().contains("contract.pdf"));
        }

        @Test
        @Order(25)
        @DisplayName("getDocuments: delegates contract lookup and manager-access check to ContractAccessGuard")
        void shouldDelegateAccessChecksToGuard() {
            Contracts contract = fakeContract();
            ContractDocument doc = fakeDoc(contract);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of(doc));

            List<ContractDocumentDTO> result = contractDocumentService.getDocuments(CONTRACT_ID);

            assertEquals(1, result.size());
            verify(contractAccessGuard).getContractInScope(CONTRACT_ID);
            verify(contractAccessGuard).checkManagerCanAccess(contract);
        }

        // ---- analyzeClauseRisk ----

        @Test
        @Order(26)
        @DisplayName("analyzeClauseRisk: extracts raw text and delegates to MlProxyService")
        void shouldAnalyzeClauseRisk() {
            Contracts contract = fakeContract();
            ContractDocument doc = fakeDoc(contract);
            ResponseEntity<String> mlResponse = ResponseEntity.ok("{\"clauses\":[]}");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(doc));
            when(localStorageService.readDocument(doc.getStoragePath())).thenReturn(VALID_PDF);
            when(pdfBoxService.extractRawText(VALID_PDF)).thenReturn("raw contract text");
            when(mlProxyService.analyzeClauseRisk("raw contract text")).thenReturn(mlResponse);

            ResponseEntity<String> result = contractDocumentService.analyzeClauseRisk(CONTRACT_ID, DOC_ID);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("{\"clauses\":[]}", result.getBody());
        }

        @Test
        @Order(27)
        @DisplayName("analyzeClauseRisk: throws ContractNotFoundException when document missing")
        void shouldThrowWhenDocumentNotFoundOnAnalyzeClauseRisk() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.analyzeClauseRisk(CONTRACT_ID, DOC_ID));
        }

        // ---- getDocuments: version collapsing ----

        @Test
        @Order(28)
        @DisplayName("getDocuments: collapses multiple versions to the latest one per group, with versionCount")
        void shouldCollapseVersionsToLatestInList() {
            Contracts contract = fakeContract();
            ContractDocument v2 = fakeDoc(contract);
            v2.setId(20L);
            v2.setVersionNumber(2);
            ContractDocument v1 = fakeDoc(contract);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of(v2, v1));

            List<ContractDocumentDTO> result = contractDocumentService.getDocuments(CONTRACT_ID);

            assertEquals(1, result.size());
            assertEquals(20L, result.get(0).id());
            assertEquals(2, result.get(0).versionCount());
        }

        // ---- uploadNewVersion ----

        @Test
        @Order(29)
        @DisplayName("uploadNewVersion: returns DTO with incremented versionNumber and correct versionCount")
        void shouldUploadNewVersionSuccessfully() throws IOException {
            Contracts contract = fakeContract();
            ContractDocument existing = fakeDoc(contract);
            ContractDocument saved = fakeDoc(contract);
            saved.setId(20L);
            saved.setVersionNumber(2);
            saved.setFileName("contract-v2.pdf");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.of(existing));
            when(documentRepository.findByVersionGroupIdOrderByVersionNumberDesc(DOC_ID))
                    .thenReturn(List.of(existing));
            when(localStorageService.storeDocument(any(), eq(CONTRACT_ID), any()))
                    .thenReturn("contracts/0/1/uuid-v2.pdf");
            when(documentRepository.save(any(ContractDocument.class))).thenReturn(saved);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract-v2.pdf", "application/pdf", VALID_PDF);

            ContractDocumentDTO result = contractDocumentService.uploadNewVersion(CONTRACT_ID, DOC_ID, file);

            assertEquals(2, result.versionNumber());
            assertEquals(2, result.versionCount());
            assertEquals(DOC_ID, result.versionGroupId());
        }

        @Test
        @Order(30)
        @DisplayName("uploadNewVersion: throws ContractNotFoundException when base document missing")
        void shouldThrowWhenBaseDocumentMissingOnNewVersion() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.empty());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract-v2.pdf", "application/pdf", VALID_PDF);

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.uploadNewVersion(CONTRACT_ID, DOC_ID, file));
        }

        @Test
        @Order(31)
        @DisplayName("uploadNewVersion: falls back to the base document's own versionNumber when no siblings are recorded yet")
        void shouldIncrementFromBaseVersionWhenNoSiblings() throws IOException {
            Contracts contract = fakeContract();
            ContractDocument existing = fakeDoc(contract);
            existing.setVersionNumber(3);
            ContractDocument saved = fakeDoc(contract);
            saved.setId(20L);
            saved.setVersionNumber(4);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.of(existing));
            when(documentRepository.findByVersionGroupIdOrderByVersionNumberDesc(DOC_ID))
                    .thenReturn(List.of());
            when(localStorageService.storeDocument(any(), eq(CONTRACT_ID), any()))
                    .thenReturn("contracts/0/1/uuid-v4.pdf");
            when(documentRepository.save(any(ContractDocument.class))).thenReturn(saved);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract-v4.pdf", "application/pdf", VALID_PDF);

            ContractDocumentDTO result = contractDocumentService.uploadNewVersion(CONTRACT_ID, DOC_ID, file);

            assertEquals(4, result.versionNumber());
            assertEquals(1, result.versionCount());
        }

        // ---- getVersions ----

        @Test
        @Order(31)
        @DisplayName("getVersions: returns full history newest first with versionCount")
        void shouldReturnVersionHistory() {
            Contracts contract = fakeContract();
            ContractDocument v1 = fakeDoc(contract);
            ContractDocument v2 = fakeDoc(contract);
            v2.setId(20L);
            v2.setVersionNumber(2);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.of(v1));
            when(documentRepository.findByVersionGroupIdOrderByVersionNumberDesc(DOC_ID))
                    .thenReturn(List.of(v2, v1));

            List<ContractDocumentDTO> result = contractDocumentService.getVersions(CONTRACT_ID, DOC_ID);

            assertEquals(2, result.size());
            assertEquals(2, result.get(0).versionNumber());
            assertEquals(1, result.get(1).versionNumber());
            assertEquals(2, result.get(0).versionCount());
        }

        @Test
        @Order(32)
        @DisplayName("getVersions: throws ContractNotFoundException when document missing")
        void shouldThrowWhenDocumentMissingOnGetVersions() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.getVersions(CONTRACT_ID, DOC_ID));
        }

        // ---- diffDocuments ----

        @Test
        @Order(33)
        @DisplayName("diffDocuments: diffs using persisted extractedText, no re-read from storage")
        void shouldDiffUsingPersistedText() {
            Contracts contract = fakeContract();
            ContractDocument from = fakeDoc(contract);
            from.setExtractedText("Amount: 1000\nOther line");
            ContractDocument to = fakeDoc(contract);
            to.setId(20L);
            to.setExtractedText("Amount: 2000\nOther line");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.of(from));
            when(documentRepository.findByIdAndContractId(20L, CONTRACT_ID)).thenReturn(Optional.of(to));

            DocumentDiffDTO result = contractDocumentService.diffDocuments(CONTRACT_ID, DOC_ID, 20L);

            assertEquals(2, result.lines().size());
            assertTrue(result.lines().stream().anyMatch(l -> "CHANGE".equals(l.tag())));
            assertTrue(result.lines().stream().anyMatch(l -> "EQUAL".equals(l.tag())));
            verify(localStorageService, never()).readDocument(any());
        }

        @Test
        @Order(34)
        @DisplayName("diffDocuments: lazily extracts and backfills text when extractedText is null")
        void shouldBackfillMissingExtractedText() {
            Contracts contract = fakeContract();
            ContractDocument from = fakeDoc(contract);
            ContractDocument to = fakeDoc(contract);
            to.setId(20L);
            to.setExtractedText("New text");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.of(from));
            when(documentRepository.findByIdAndContractId(20L, CONTRACT_ID)).thenReturn(Optional.of(to));
            when(localStorageService.readDocument(from.getStoragePath())).thenReturn(VALID_PDF);
            when(pdfBoxService.extractRawText(VALID_PDF)).thenReturn("Old text");

            contractDocumentService.diffDocuments(CONTRACT_ID, DOC_ID, 20L);

            assertEquals("Old text", from.getExtractedText());
            verify(documentRepository).save(from);
        }

        @Test
        @Order(35)
        @DisplayName("diffDocuments: throws ContractNotFoundException when the second document is missing")
        void shouldThrowWhenSecondDocumentMissingOnDiff() {
            Contracts contract = fakeContract();
            ContractDocument from = fakeDoc(contract);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.of(from));
            when(documentRepository.findByIdAndContractId(20L, CONTRACT_ID)).thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.diffDocuments(CONTRACT_ID, DOC_ID, 20L));
        }

        @Test
        @Order(36)
        @DisplayName("diffDocuments: throws ContractNotFoundException when the first document is missing")
        void shouldThrowWhenFirstDocumentMissingOnDiff() {
            Contracts contract = fakeContract();

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.diffDocuments(CONTRACT_ID, DOC_ID, 20L));
        }

        @Test
        @Order(37)
        @DisplayName("diffDocuments: treats extraction failure as empty text instead of propagating")
        void shouldTreatExtractionFailureAsEmptyText() {
            Contracts contract = fakeContract();
            ContractDocument from = fakeDoc(contract);
            ContractDocument to = fakeDoc(contract);
            to.setId(20L);
            to.setExtractedText("Some text");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.of(from));
            when(documentRepository.findByIdAndContractId(20L, CONTRACT_ID)).thenReturn(Optional.of(to));
            when(localStorageService.readDocument(from.getStoragePath())).thenReturn(VALID_PDF);
            when(pdfBoxService.extractRawText(VALID_PDF)).thenThrow(new RuntimeException());

            DocumentDiffDTO result = contractDocumentService.diffDocuments(CONTRACT_ID, DOC_ID, 20L);

            assertNull(from.getExtractedText());
            verify(documentRepository, never()).save(from);
            assertTrue(result.lines().stream().allMatch(l -> "INSERT".equals(l.tag())));
        }

        @Test
        @Order(38)
        @DisplayName("diffDocuments: sanitizes CRLF in a logged extraction failure message")
        void shouldSanitizeExtractionFailureMessage() {
            Contracts contract = fakeContract();
            ContractDocument from = fakeDoc(contract);
            ContractDocument to = fakeDoc(contract);
            to.setId(20L);
            to.setExtractedText("Some text");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.of(from));
            when(documentRepository.findByIdAndContractId(20L, CONTRACT_ID)).thenReturn(Optional.of(to));
            when(localStorageService.readDocument(from.getStoragePath())).thenReturn(VALID_PDF);
            when(pdfBoxService.extractRawText(VALID_PDF)).thenThrow(new RuntimeException("disk read error"));

            contractDocumentService.diffDocuments(CONTRACT_ID, DOC_ID, 20L);

            assertNull(from.getExtractedText());
        }

        @Test
        @Order(38)
        @DisplayName("diffDocuments: reports INSERT and DELETE rows for lines unique to one side")
        void shouldReportInsertAndDeleteRows() {
            Contracts contract = fakeContract();
            ContractDocument from = fakeDoc(contract);
            from.setExtractedText("Line1\nRemoved line\nLine3");
            ContractDocument to = fakeDoc(contract);
            to.setId(20L);
            to.setExtractedText("Line1\nLine3\nAdded line");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID)).thenReturn(Optional.of(from));
            when(documentRepository.findByIdAndContractId(20L, CONTRACT_ID)).thenReturn(Optional.of(to));

            DocumentDiffDTO result = contractDocumentService.diffDocuments(CONTRACT_ID, DOC_ID, 20L);

            assertTrue(result.lines().stream()
                    .anyMatch(l -> "DELETE".equals(l.tag()) && l.newText() == null));
            assertTrue(result.lines().stream()
                    .anyMatch(l -> "INSERT".equals(l.tag()) && l.oldText() == null));
        }
    }
}
