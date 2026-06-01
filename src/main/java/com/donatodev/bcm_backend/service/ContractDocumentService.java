package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractDocumentDTO;
import com.donatodev.bcm_backend.dto.TextractResultDTO;
import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;

@Service
public class ContractDocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10 MB
    private static final byte[] PDF_MAGIC = new byte[]{'%', 'P', 'D', 'F'};

    private final ContractDocumentRepository documentRepository;
    private final ContractsRepository contractsRepository;
    private final S3Service s3Service;
    private final TextractService textractService;

    public ContractDocumentService(ContractDocumentRepository documentRepository,
                                   ContractsRepository contractsRepository,
                                   S3Service s3Service,
                                   TextractService textractService) {
        this.documentRepository = documentRepository;
        this.contractsRepository = contractsRepository;
        this.s3Service = s3Service;
        this.textractService = textractService;
    }

    @Transactional
    public ContractDocumentDTO uploadDocument(Long contractId, MultipartFile file) throws IOException {
        Contracts contract = contractsRepository.findById(contractId)
                .orElseThrow(() -> new ContractNotFoundException("Contract ID " + contractId + " not found"));

        validateFile(file);

        Long orgId = TenantContext.get();
        byte[] bytes = file.getBytes();
        String s3Key = s3Service.uploadDocument(orgId, contractId,
                file.getOriginalFilename(), file.getContentType(), bytes);

        ContractDocument doc = documentRepository.save(ContractDocument.builder()
                .contract(contract)
                .s3Key(s3Key)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .orgId(orgId)
                .build());

        return toDTO(doc, s3Service.generatePresignedUrl(s3Key));
    }

    @Transactional(readOnly = true)
    public List<ContractDocumentDTO> getDocuments(Long contractId) {
        return documentRepository.findByContractIdOrderByUploadedAtDesc(contractId)
                .stream()
                .map(doc -> toDTO(doc, s3Service.generatePresignedUrl(doc.getS3Key())))
                .toList();
    }

    public TextractResultDTO extractText(Long contractId, Long documentId) {
        ContractDocument doc = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        "Document ID " + documentId + " not found for contract " + contractId));

        return textractService.extractFromS3(doc.getId(), doc.getS3Key());
    }

    @Transactional
    public void deleteDocument(Long contractId, Long documentId) {
        ContractDocument doc = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        "Document ID " + documentId + " not found for contract " + contractId));

        s3Service.deleteDocument(doc.getS3Key());
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

    private ContractDocumentDTO toDTO(ContractDocument doc, String downloadUrl) {
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
