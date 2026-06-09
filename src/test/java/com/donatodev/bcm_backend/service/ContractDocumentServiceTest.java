package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.dto.ContractDocumentDTO;
import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;
import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.service.ContractDocumentService.DocumentDownload;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ContractDocumentServiceTest {

    @Mock private ContractDocumentRepository documentRepository;
    @Mock private ContractsRepository contractsRepository;
    @Mock private LocalStorageService localStorageService;
    @Mock private PdfBoxService pdfBoxService;

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

            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
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
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.empty());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(3)
        @DisplayName("uploadDocument: throws IllegalArgumentException on empty file")
        void shouldThrowOnEmptyFile() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.pdf", "application/pdf", new byte[0]);

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(4)
        @DisplayName("uploadDocument: throws IllegalArgumentException when file too large")
        void shouldThrowWhenFileTooLarge() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));

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
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "image.png", "image/png", "not a pdf content".getBytes());

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(6)
        @DisplayName("uploadDocument: throws IllegalArgumentException when file shorter than 4 bytes")
        void shouldThrowOnTooShortFile() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "tiny.pdf", "application/pdf", new byte[]{'%', 'P'});

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(6)
        @DisplayName("uploadDocument: throws when byte[1] is wrong (not 'P')")
        void shouldThrowWhenSecondByteMismatch() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "file.pdf", "application/pdf", "%XDF test".getBytes());

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(7)
        @DisplayName("uploadDocument: throws when byte[2] is wrong (not 'D')")
        void shouldThrowWhenThirdByteMismatch() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "file.pdf", "application/pdf", "%PXF test".getBytes());

            assertThrows(IllegalArgumentException.class,
                    () -> contractDocumentService.uploadDocument(CONTRACT_ID, file));
        }

        @Test
        @Order(8)
        @DisplayName("uploadDocument: throws when byte[3] is wrong (not 'F')")
        void shouldThrowWhenFourthByteMismatch() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));

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

            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(doc));
            when(localStorageService.readDocument(doc.getStoragePath())).thenReturn(VALID_PDF);

            DocumentDownload result = contractDocumentService.downloadDocument(CONTRACT_ID, DOC_ID);

            assertNotNull(result.bytes());
            assertEquals("contract.pdf", result.fileName());
            assertEquals("application/pdf", result.contentType());
        }

        @Test
        @Order(14)
        @DisplayName("downloadDocument: throws ContractNotFoundException when document missing")
        void shouldThrowWhenDocumentNotFoundOnDownload() {
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
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.deleteDocument(CONTRACT_ID, DOC_ID));
        }

        // ---- DocumentDownload record ----

        @Test
        @Order(17)
        @DisplayName("DocumentDownload: equals same reference returns true")
        void documentDownloadEqualsSameReference() {
            DocumentDownload dd = new DocumentDownload(VALID_PDF, "f.pdf", "application/pdf");
            assertEquals(dd, dd);
        }

        @Test
        @Order(18)
        @DisplayName("DocumentDownload: equals null returns false")
        void documentDownloadEqualsNull() {
            DocumentDownload dd = new DocumentDownload(VALID_PDF, "f.pdf", "application/pdf");
            boolean result = dd.equals(null);
            assertFalse(result);
        }

        @Test
        @Order(19)
        @DisplayName("DocumentDownload: equals different type returns false")
        void documentDownloadEqualsDifferentType() {
            DocumentDownload dd = new DocumentDownload(VALID_PDF, "f.pdf", "application/pdf");
            boolean result = dd.equals("string");
            assertFalse(result);
        }

        @Test
        @Order(20)
        @DisplayName("DocumentDownload: equals same content returns true, hashCode matches")
        void documentDownloadEqualsAndHashCode() {
            DocumentDownload dd1 = new DocumentDownload(VALID_PDF, "f.pdf", "application/pdf");
            DocumentDownload dd2 = new DocumentDownload(VALID_PDF, "f.pdf", "application/pdf");
            assertEquals(dd1, dd2);
            assertEquals(dd2.hashCode(), dd1.hashCode());
        }

        @Test
        @Order(22)
        @DisplayName("DocumentDownload: equals returns false when bytes differ")
        void documentDownloadNotEqualsDifferentBytes() {
            DocumentDownload dd1 = new DocumentDownload(VALID_PDF, "f.pdf", "application/pdf");
            DocumentDownload dd2 = new DocumentDownload(new byte[]{1, 2, 3}, "f.pdf", "application/pdf");
            assertNotEquals(dd1, dd2);
        }

        @Test
        @Order(23)
        @DisplayName("DocumentDownload: equals returns false when fileName differs")
        void documentDownloadNotEqualsDifferentFileName() {
            DocumentDownload dd1 = new DocumentDownload(VALID_PDF, "a.pdf", "application/pdf");
            DocumentDownload dd2 = new DocumentDownload(VALID_PDF, "b.pdf", "application/pdf");
            assertNotEquals(dd1, dd2);
        }

        @Test
        @Order(24)
        @DisplayName("DocumentDownload: equals returns false when contentType differs")
        void documentDownloadNotEqualsDifferentContentType() {
            DocumentDownload dd1 = new DocumentDownload(VALID_PDF, "f.pdf", "application/pdf");
            DocumentDownload dd2 = new DocumentDownload(VALID_PDF, "f.pdf", "text/plain");
            assertNotEquals(dd1, dd2);
        }

        @Test
        @Order(21)
        @DisplayName("DocumentDownload: toString contains fileName")
        void documentDownloadToString() {
            DocumentDownload dd = new DocumentDownload(VALID_PDF, "contract.pdf", "application/pdf");
            assertTrue(dd.toString().contains("contract.pdf"));
        }
    }
}
