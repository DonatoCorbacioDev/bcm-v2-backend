package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.RiskFeedbackDTO;
import com.donatodev.bcm_backend.dto.RiskFeedbackRequest;
import com.donatodev.bcm_backend.service.RiskFeedbackService;

import jakarta.validation.Valid;

/**
 * Endpoints for confirming or disputing risk scores shown on a contract,
 * so agreement/disagreement with the model can be tracked over time.
 */
@RestController
@RequestMapping("/risk-feedback")
public class RiskFeedbackController {

    private final RiskFeedbackService riskFeedbackService;

    public RiskFeedbackController(RiskFeedbackService riskFeedbackService) {
        this.riskFeedbackService = riskFeedbackService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @GetMapping
    public ResponseEntity<List<RiskFeedbackDTO>> getFeedback() {
        return ResponseEntity.ok(riskFeedbackService.getFeedbackForCurrentUser());
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PostMapping("/contracts/{contractId}")
    public ResponseEntity<RiskFeedbackDTO> submitFeedback(
            @PathVariable Long contractId,
            @Valid @RequestBody RiskFeedbackRequest request) {
        RiskFeedbackDTO created = riskFeedbackService.create(contractId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
