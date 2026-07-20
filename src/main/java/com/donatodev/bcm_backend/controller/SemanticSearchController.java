package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.SemanticSearchRequestDTO;
import com.donatodev.bcm_backend.dto.SemanticSearchResultDTO;
import com.donatodev.bcm_backend.service.SemanticSearchService;

import jakarta.validation.Valid;

/** Not nested under /contracts/{contractId}/documents like the rest of
 * ContractDocumentController — this searches across every contract's
 * documents in the caller's organization, not one contract's. */
@RestController
@RequestMapping("/contracts/search")
public class SemanticSearchController {

    private static final int TOP_K = 10;

    private final SemanticSearchService semanticSearchService;

    public SemanticSearchController(SemanticSearchService semanticSearchService) {
        this.semanticSearchService = semanticSearchService;
    }

    @PostMapping("/semantic")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<SemanticSearchResultDTO>> semanticSearch(
            @Valid @RequestBody SemanticSearchRequestDTO request) {
        return ResponseEntity.ok(semanticSearchService.search(request.query(), TOP_K));
    }
}
