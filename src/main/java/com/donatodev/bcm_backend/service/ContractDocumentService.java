package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractDocumentDTO;
import com.donatodev.bcm_backend.dto.DiffLineDTO;
import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;
import com.donatodev.bcm_backend.dto.DocumentDiffDTO;
import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

@Service
public class ContractDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(ContractDocumentService.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
    private static final byte[] PDF_MAGIC = new byte[]{'%', 'P', 'D', 'F'};
    private static final String DOC_NOT_FOUND = "Documento ID %d non trovato per il contratto %d";
    private static final String LINE_SPLIT_REGEX = "\\r\\n|\\r|\\n";
    private static final String CRLF_REGEX = "[\r\n]";

    @Value("${app.backend-base-url:http://localhost:8090/api/v1}")
    private String backendBaseUrl;

    private final ContractDocumentRepository documentRepository;
    private final ContractAccessGuard contractAccessGuard;
    private final LocalStorageService localStorageService;
    private final PdfBoxService pdfBoxService;
    private final MlProxyService mlProxyService;
    private final SemanticSearchService semanticSearchService;

    public ContractDocumentService(ContractDocumentRepository documentRepository,
                                   ContractAccessGuard contractAccessGuard,
                                   LocalStorageService localStorageService,
                                   PdfBoxService pdfBoxService,
                                   MlProxyService mlProxyService,
                                   SemanticSearchService semanticSearchService) {
        this.documentRepository = documentRepository;
        this.contractAccessGuard = contractAccessGuard;
        this.localStorageService = localStorageService;
        this.pdfBoxService = pdfBoxService;
        this.mlProxyService = mlProxyService;
        this.semanticSearchService = semanticSearchService;
    }

    @Transactional(rollbackFor = IOException.class)
    public ContractDocumentDTO uploadDocument(Long contractId, MultipartFile file) throws IOException {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        validateFile(file);

        Long orgId = TenantContext.get();
        ContractDocument doc = saveNewVersion(contract, orgId, file, null, 1);
        return toDTO(doc, 1);
    }

    @Transactional(rollbackFor = IOException.class)
    public ContractDocumentDTO uploadNewVersion(Long contractId, Long documentId, MultipartFile file) throws IOException {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        validateFile(file);

        ContractDocument existing = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, documentId, contractId)));

        List<ContractDocument> siblings = documentRepository
                .findByVersionGroupIdOrderByVersionNumberDesc(existing.getVersionGroupId());
        int nextVersion = siblings.isEmpty()
                ? existing.getVersionNumber() + 1
                : siblings.get(0).getVersionNumber() + 1;

        Long orgId = TenantContext.get();
        ContractDocument doc = saveNewVersion(contract, orgId, file, existing.getVersionGroupId(), nextVersion);
        return toDTO(doc, siblings.size() + 1);
    }

    @Transactional(readOnly = true)
    public List<ContractDocumentDTO> getVersions(Long contractId, Long documentId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);

        ContractDocument existing = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, documentId, contractId)));

        List<ContractDocument> versions = documentRepository
                .findByVersionGroupIdOrderByVersionNumberDesc(existing.getVersionGroupId());
        return versions.stream().map(d -> toDTO(d, versions.size())).toList();
    }

    public DocumentDiffDTO diffDocuments(Long contractId, Long fromDocumentId, Long toDocumentId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);

        ContractDocument from = documentRepository.findByIdAndContractId(fromDocumentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, fromDocumentId, contractId)));
        ContractDocument to = documentRepository.findByIdAndContractId(toDocumentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, toDocumentId, contractId)));

        List<String> oldLines = splitLines(getOrExtractText(from));
        List<String> newLines = splitLines(getOrExtractText(to));

        DiffRowGenerator generator = DiffRowGenerator.create()
                .showInlineDiffs(false)
                .ignoreWhiteSpaces(true)
                .build();
        List<DiffRow> rows = generator.generateDiffRows(oldLines, newLines);

        List<DiffLineDTO> lines = rows.stream()
                .map(row -> new DiffLineDTO(
                        row.getTag().name(),
                        row.getTag() == DiffRow.Tag.INSERT ? null : row.getOldLine(),
                        row.getTag() == DiffRow.Tag.DELETE ? null : row.getNewLine()))
                .toList();

        return new DocumentDiffDTO(from.getId(), from.getFileName(), to.getId(), to.getFileName(), lines);
    }

    @Transactional(readOnly = true)
    public List<ContractDocumentDTO> getDocuments(Long contractId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);

        List<ContractDocument> all = documentRepository.findByContractIdOrderByUploadedAtDesc(contractId);

        // Only the latest version of each version group is shown in the main
        // list; older versions are reached via getVersions(). Input is already
        // ordered newest-first, so the first document seen per group is both
        // the most recently uploaded and (by construction) the highest version.
        Map<Long, ContractDocument> latestByGroup = new LinkedHashMap<>();
        Map<Long, Integer> countByGroup = new HashMap<>();
        for (ContractDocument doc : all) {
            countByGroup.merge(doc.getVersionGroupId(), 1, Integer::sum);
            latestByGroup.putIfAbsent(doc.getVersionGroupId(), doc);
        }

        return latestByGroup.values().stream()
                .map(doc -> toDTO(doc, countByGroup.get(doc.getVersionGroupId())))
                .toList();
    }

    public DocumentAnalysisDTO extractText(Long contractId, Long documentId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ContractDocument doc = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, documentId, contractId)));

        byte[] bytes = localStorageService.readDocument(doc.getStoragePath());
        DocumentAnalysisDTO analysis = pdfBoxService.analyzeDocument(doc.getId(), bytes);
        semanticSearchService.generateAndStoreEmbedding(doc, analysis.rawText());
        return analysis;
    }

    public ResponseEntity<String> analyzeClauseRisk(Long contractId, Long documentId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ContractDocument doc = documentRepository.findByIdAndContractId(documentId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(DOC_NOT_FOUND, documentId, contractId)));

        byte[] bytes = localStorageService.readDocument(doc.getStoragePath());
        String rawText = pdfBoxService.extractRawText(bytes);
        return mlProxyService.analyzeClauseRisk(rawText);
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

    private ContractDocument saveNewVersion(Contracts contract, Long orgId, MultipartFile file,
                                             Long versionGroupId, int versionNumber) throws IOException {
        byte[] bytes = file.getBytes();
        String storagePath = localStorageService.storeDocument(orgId, contract.getId(), bytes);

        ContractDocument doc = documentRepository.save(ContractDocument.builder()
                .contract(contract)
                .storagePath(storagePath)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .orgId(orgId)
                .versionGroupId(versionGroupId)
                .versionNumber(versionNumber)
                .extractedText(extractTextBestEffort(bytes))
                .build());

        if (versionGroupId == null) {
            doc.setVersionGroupId(doc.getId());
            doc = documentRepository.save(doc);
        }
        return doc;
    }

    /**
     * Falls back to persisted extractedText; if a document predates the
     * versioning migration (or extraction failed at upload time), extracts
     * on demand and backfills it so the next diff is instant.
     */
    private String getOrExtractText(ContractDocument doc) {
        if (doc.getExtractedText() != null) {
            return doc.getExtractedText();
        }
        byte[] bytes = localStorageService.readDocument(doc.getStoragePath());
        String text = extractTextBestEffort(bytes);
        if (text != null) {
            doc.setExtractedText(text);
            documentRepository.save(doc);
        }
        return text == null ? "" : text;
    }

    /**
     * Best-effort: OCR/PDFBox failures must not block the upload they ride
     * along with. A null result just means the diff view backfills later.
     */
    private String extractTextBestEffort(byte[] bytes) {
        try {
            return pdfBoxService.extractRawText(bytes);
        } catch (Exception e) {
            logger.warn("Text extraction failed at upload time: {}", safeMessage(e));
            return null;
        }
    }

    // Only ever called with getOrExtractText()'s result, which is guaranteed
    // non-null (see its own null-to-"" fallback) — no null check needed here.
    private List<String> splitLines(String text) {
        return text.isEmpty() ? List.of() : Arrays.asList(text.split(LINE_SPLIT_REGEX, -1));
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? null : message.replaceAll(CRLF_REGEX, "_");
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Il file è vuoto");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Il file supera il limite di 10 MB");
        }
        byte[] header = file.getBytes();
        if (header.length < 4 ||
                header[0] != PDF_MAGIC[0] || header[1] != PDF_MAGIC[1] ||
                header[2] != PDF_MAGIC[2] || header[3] != PDF_MAGIC[3]) {
            throw new IllegalArgumentException("Sono supportati solo file PDF");
        }
    }

    private ContractDocumentDTO toDTO(ContractDocument doc, int versionCount) {
        String downloadUrl = String.format("%s/contracts/%d/documents/%d/download",
                backendBaseUrl, doc.getContract().getId(), doc.getId());
        return new ContractDocumentDTO(
                doc.getId(),
                doc.getContract().getId(),
                doc.getFileName(),
                doc.getFileSize(),
                doc.getContentType(),
                doc.getUploadedAt(),
                downloadUrl,
                doc.getVersionGroupId(),
                doc.getVersionNumber(),
                versionCount);
    }
}
