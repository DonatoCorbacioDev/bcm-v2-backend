package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.ContractDocumentDTO;
import com.donatodev.bcm_backend.dto.TextractResultDTO;
import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ContractDocumentServiceTest {

    @Mock private ContractDocumentRepository documentRepository;
    @Mock private ContractsRepository contractsRepository;
    @Mock private S3Service s3Service;
    @Mock private TextractService textractService;

    @InjectMocks
    private ContractDocumentService contractDocumentService;

    private static final byte[] VALID_PDF = "%PDF-1.4 test".getBytes();
    private static final long CONTRACT_ID = 1L;
    private static final long DOC_ID = 10L;

    private Contracts fakeContract() {
        Contracts c = new Contracts();
        c.setId(CONTRACT_ID);
        return c;
    }

    private ContractDocument fakeDoc(Contracts contract) {
        ContractDocument doc = new ContractDocument();
        doc.setId(DOC_ID);
        doc.setContract(contract);
        doc.setS3Key("contracts/0/1/uuid-contract.pdf");
        doc.setFileName("contract.pdf");
        doc.setFileSize((long) VALID_PDF.length);
        doc.setContentType("application/pdf");
        doc.setUploadedAt(Instant.now());
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
            when(s3Service.uploadDocument(any(), eq(CONTRACT_ID), anyString(), anyString(), any()))
                    .thenReturn("contracts/0/1/uuid-contract.pdf");
            when(documentRepository.save(any(ContractDocument.class))).thenReturn(saved);
            when(s3Service.generatePresignedUrl(anyString())).thenReturn("https://s3.example.com/url");

            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);

            ContractDocumentDTO result = contractDocumentService.uploadDocument(CONTRACT_ID, file);

            assertNotNull(result);
            assertEquals("contract.pdf", result.fileName());
            assertEquals("https://s3.example.com/url", result.downloadUrl());
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

            // > 10 MB
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

        // ---- getDocuments ----

        @Test
        @Order(7)
        @DisplayName("getDocuments: returns mapped DTOs with presigned URLs")
        void shouldReturnDocumentList() {
            Contracts contract = fakeContract();
            ContractDocument doc = fakeDoc(contract);

            when(documentRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of(doc));
            when(s3Service.generatePresignedUrl(anyString()))
                    .thenReturn("https://s3.example.com/url");

            List<ContractDocumentDTO> result = contractDocumentService.getDocuments(CONTRACT_ID);

            assertEquals(1, result.size());
            assertEquals(DOC_ID, result.get(0).id());
            assertEquals("https://s3.example.com/url", result.get(0).downloadUrl());
        }

        @Test
        @Order(8)
        @DisplayName("getDocuments: returns empty list when no documents")
        void shouldReturnEmptyList() {
            when(documentRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of());

            List<ContractDocumentDTO> result = contractDocumentService.getDocuments(CONTRACT_ID);

            assertEquals(0, result.size());
        }

        // ---- extractText ----

        @Test
        @Order(9)
        @DisplayName("extractText: delegates to TextractService and returns result")
        void shouldExtractText() {
            Contracts contract = fakeContract();
            ContractDocument doc = fakeDoc(contract);
            TextractResultDTO expected = new TextractResultDTO(
                    DOC_ID, "raw text", "Acme", null, null, null, null);

            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(doc));
            when(textractService.extractFromS3(DOC_ID, doc.getS3Key())).thenReturn(expected);

            TextractResultDTO result = contractDocumentService.extractText(CONTRACT_ID, DOC_ID);

            assertEquals("Acme", result.detectedCustomerName());
        }

        @Test
        @Order(10)
        @DisplayName("extractText: throws ContractNotFoundException when document missing")
        void shouldThrowWhenDocumentNotFoundOnExtract() {
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.extractText(CONTRACT_ID, DOC_ID));
        }

        // ---- deleteDocument ----

        @Test
        @Order(11)
        @DisplayName("deleteDocument: deletes from S3 and repository")
        void shouldDeleteDocument() {
            Contracts contract = fakeContract();
            ContractDocument doc = fakeDoc(contract);

            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(doc));

            contractDocumentService.deleteDocument(CONTRACT_ID, DOC_ID);

            verify(s3Service).deleteDocument(doc.getS3Key());
            verify(documentRepository).delete(doc);
        }

        @Test
        @Order(12)
        @DisplayName("deleteDocument: throws ContractNotFoundException when document missing")
        void shouldThrowWhenDocumentNotFoundOnDelete() {
            when(documentRepository.findByIdAndContractId(DOC_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractDocumentService.deleteDocument(CONTRACT_ID, DOC_ID));
        }
    }
}
