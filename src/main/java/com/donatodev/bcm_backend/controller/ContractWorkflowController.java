package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.ContractWorkflowEventDTO;
import com.donatodev.bcm_backend.dto.RejectContractRequest;
import com.donatodev.bcm_backend.service.ContractWorkflowService;

/**
 * REST controller for the contract approval workflow: submit for review,
 * approve, reject, and view the workflow history.
 */
@RestController
@RequestMapping("/contracts/{contractId}/workflow")
public class ContractWorkflowController {

    private final ContractWorkflowService contractWorkflowService;

    public ContractWorkflowController(ContractWorkflowService contractWorkflowService) {
        this.contractWorkflowService = contractWorkflowService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/submit")
    public ResponseEntity<Void> submitForReview(@PathVariable Long contractId) {
        contractWorkflowService.submitForReview(contractId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/approve")
    public ResponseEntity<Void> approve(@PathVariable Long contractId) {
        contractWorkflowService.approve(contractId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/reject")
    public ResponseEntity<Void> reject(@PathVariable Long contractId, @RequestBody RejectContractRequest request) {
        contractWorkflowService.reject(contractId, request.comment());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/events")
    public ResponseEntity<List<ContractWorkflowEventDTO>> getEvents(@PathVariable Long contractId) {
        return ResponseEntity.ok(contractWorkflowService.getEvents(contractId));
    }
}
