package com.donatodev.bcm_backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.donatodev.bcm_backend.dto.ContractDocumentDTO;
import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;
import com.donatodev.bcm_backend.service.ContractDocumentService;
import com.donatodev.bcm_backend.service.ContractDocumentService.DocumentDownload;

@RestController
@RequestMapping("/contracts/{contractId}/documents")
public class ContractDocumentController {

    private final ContractDocumentService documentService;

    public ContractDocumentController(ContractDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ContractDocumentDTO> uploadDocument(
            @PathVariable Long contractId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.uploadDocument(contractId, file));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<ContractDocumentDTO>> getDocuments(@PathVariable Long contractId) {
        return ResponseEntity.ok(documentService.getDocuments(contractId));
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long contractId,
            @PathVariable Long documentId) {
        DocumentDownload download = documentService.downloadDocument(contractId, documentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.fileName()).build());
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        return ResponseEntity.ok().headers(headers).body(download.bytes());
    }

    @PostMapping("/{documentId}/extract")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<DocumentAnalysisDTO> extractText(
            @PathVariable Long contractId,
            @PathVariable Long documentId) {
        return ResponseEntity.ok(documentService.extractText(contractId, documentId));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long contractId,
            @PathVariable Long documentId) {
        documentService.deleteDocument(contractId, documentId);
        return ResponseEntity.noContent().build();
    }
}
