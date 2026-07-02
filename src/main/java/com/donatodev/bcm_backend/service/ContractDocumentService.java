package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractDocumentDTO;
import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;
import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;

@Service
public class ContractDocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
    private static final byte[] PDF_MAGIC = new byte[]{'%', 'P', 'D', 'F'};
    private static final String DOC_NOT_FOUND = "Document ID %d not found for contract %d";

    @Value("${app.backend-base-url:http://localhost:8090/api/v1}")
    private String backendBaseUrl;

    private final ContractDocumentRepository documentRepository;
    private final ContractAccessGuard contractAccessGuard;
    private final LocalStorageService localStorageService;
    private final PdfBoxService pdfBoxService;

    public ContractDocumentService(ContractDocumentRepository documentRepository,
                                   ContractAccessGuard contractAccessGuard,
                                   LocalStorageService localStorageService,
                                   PdfBoxService pdfBoxService) {
        this.documentRepository = documentRepository;
        this.contractAccessGuard = contractAccessGuard;
        this.localStorageService = localStorageService;
        this.pdfBoxService = pdfBoxService;
    }

    @Transactional
    public ContractDocumentDTO uploadDocument(Long contractId, MultipartFile file) throws IOException {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);

        validateFile(file);

        Long orgId = TenantContext.get();
        byte[] bytes = file.getBytes();
        String storagePath = localStorageService.storeDocument(orgId, contractId, bytes);

        ContractDocument doc = documentRepository.save(ContractDocument.builder()
                .contract(contract)
                .storagePath(storagePath)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .orgId(orgId)
                .build());

        return toDTO(doc);
    }

    @Transactional(readOnly = true)
    public List<ContractDocumentDTO> getDocuments(Long contractId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        return documentRepository.findByContractIdOrderByUploadedAtDesc(contractId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public DocumentAnalysisDTO extractText(Long contractId, Long documentId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ContractDocument doc = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, documentId, contractId)));

        byte[] bytes = localStorageService.readDocument(doc.getStoragePath());
        return pdfBoxService.analyzeDocument(doc.getId(), bytes);
    }

    @Transactional(readOnly = true)
    public FileDownload downloadDocument(Long contractId, Long documentId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ContractDocument doc = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, documentId, contractId)));

        byte[] bytes = localStorageService.readDocument(doc.getStoragePath());
        return new FileDownload(bytes, doc.getFileName(), doc.getContentType());
    }

    @Transactional
    public void deleteDocument(Long contractId, Long documentId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ContractDocument doc = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, documentId, contractId)));

        localStorageService.deleteDocument(doc.getStoragePath());
        documentRepository.delete(doc);
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds the 10 MB limit");
        }
        byte[] header = file.getBytes();
        if (header.length < 4 ||
                header[0] != PDF_MAGIC[0] || header[1] != PDF_MAGIC[1] ||
                header[2] != PDF_MAGIC[2] || header[3] != PDF_MAGIC[3]) {
            throw new IllegalArgumentException("Only PDF files are accepted");
        }
    }

    private ContractDocumentDTO toDTO(ContractDocument doc) {
        String downloadUrl = String.format("%s/contracts/%d/documents/%d/download",
                backendBaseUrl, doc.getContract().getId(), doc.getId());
        return new ContractDocumentDTO(
                doc.getId(),
                doc.getContract().getId(),
                doc.getFileName(),
                doc.getFileSize(),
                doc.getContentType(),
                doc.getUploadedAt(),
                downloadUrl);
    }
}
