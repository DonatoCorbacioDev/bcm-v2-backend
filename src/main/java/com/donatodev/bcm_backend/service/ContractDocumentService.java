package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractDocumentDTO;
import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;
import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@Service
public class ContractDocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
    private static final byte[] PDF_MAGIC = new byte[]{'%', 'P', 'D', 'F'};
    private static final String DOC_NOT_FOUND = "Document ID %d not found for contract %d";

    @Value("${app.backend-base-url:http://localhost:8090/api/v1}")
    private String backendBaseUrl;

    private final ContractDocumentRepository documentRepository;
    private final ContractsRepository contractsRepository;
    private final LocalStorageService localStorageService;
    private final PdfBoxService pdfBoxService;
    private final UsersRepository usersRepository;

    public ContractDocumentService(ContractDocumentRepository documentRepository,
                                   ContractsRepository contractsRepository,
                                   LocalStorageService localStorageService,
                                   PdfBoxService pdfBoxService,
                                   UsersRepository usersRepository) {
        this.documentRepository = documentRepository;
        this.contractsRepository = contractsRepository;
        this.localStorageService = localStorageService;
        this.pdfBoxService = pdfBoxService;
        this.usersRepository = usersRepository;
    }

    @Transactional
    public ContractDocumentDTO uploadDocument(Long contractId, MultipartFile file) throws IOException {
        Contracts contract = getContractInScope(contractId);
        checkManagerCanAccess(contract);

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
        Contracts contract = getContractInScope(contractId);
        checkManagerCanAccess(contract);
        return documentRepository.findByContractIdOrderByUploadedAtDesc(contractId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public DocumentAnalysisDTO extractText(Long contractId, Long documentId) {
        Contracts contract = getContractInScope(contractId);
        checkManagerCanAccess(contract);
        ContractDocument doc = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, documentId, contractId)));

        byte[] bytes = localStorageService.readDocument(doc.getStoragePath());
        return pdfBoxService.analyzeDocument(doc.getId(), bytes);
    }

    @Transactional(readOnly = true)
    public FileDownload downloadDocument(Long contractId, Long documentId) {
        Contracts contract = getContractInScope(contractId);
        checkManagerCanAccess(contract);
        ContractDocument doc = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, documentId, contractId)));

        byte[] bytes = localStorageService.readDocument(doc.getStoragePath());
        return new FileDownload(bytes, doc.getFileName(), doc.getContentType());
    }

    @Transactional
    public void deleteDocument(Long contractId, Long documentId) {
        Contracts contract = getContractInScope(contractId);
        checkManagerCanAccess(contract);
        ContractDocument doc = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, documentId, contractId)));

        localStorageService.deleteDocument(doc.getStoragePath());
        documentRepository.delete(doc);
    }

    /**
     * Throws {@link AccessDeniedException} when the authenticated user is a MANAGER
     * not assigned to the given contract. Skips the check when no auth context is present.
     */
    private void checkManagerCanAccess(Contracts contract) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }
        Long orgId = TenantContext.get();
        if (orgId == null) {
            return;
        }
        Users user = usersRepository.findByUsernameAndOrganizationId(auth.getName(), orgId).orElse(null);
        if (user == null || !"MANAGER".equals(user.getRole().getRole())) {
            return;
        }
        Long managerId = user.getManager() != null ? user.getManager().getId() : null;
        Long contractManagerId = contract.getManager() != null ? contract.getManager().getId() : null;
        if (managerId == null || !managerId.equals(contractManagerId)) {
            throw new AccessDeniedException("Not authorized to access contract: " + contract.getId());
        }
    }

    /**
     * Finds the contract by ID, scoped to the current tenant when
     * {@link TenantContext} carries an organization ID. Used to prevent
     * cross-tenant access to a contract's documents by ID.
     */
    private Contracts getContractInScope(Long contractId) {
        Long orgId = TenantContext.get();
        Optional<Contracts> contract = (orgId != null)
                ? contractsRepository.findByIdAndOrganization_Id(contractId, orgId)
                : contractsRepository.findById(contractId);
        return contract.orElseThrow(() -> new ContractNotFoundException("Contract ID " + contractId + " not found"));
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
