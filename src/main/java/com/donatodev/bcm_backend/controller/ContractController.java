package com.donatodev.bcm_backend.controller;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.AssignManagerRequest;
import com.donatodev.bcm_backend.dto.CollaboratorsRequest;
import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ContractStatsResponse;
import com.donatodev.bcm_backend.dto.ContractsByAreaDTO;
import com.donatodev.bcm_backend.dto.ContractsTimelineDTO;
import com.donatodev.bcm_backend.dto.TopManagerDTO;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.service.ContractService;
import com.donatodev.bcm_backend.service.ExportService;
import com.itextpdf.text.DocumentException;

/**
 * REST controller for managing company contracts.
 * <p>
 * Provides endpoints for creating, retrieving, updating, deleting, and
 * filtering contracts. Access to endpoints is controlled by roles (ADMIN,
 * MANAGER).
 */
@RestController
@RequestMapping("/contracts")
public class ContractController {

    private static final Logger logger = LoggerFactory.getLogger(ContractController.class);

    private final ContractService contractService;
    private final ExportService exportService;

    public ContractController(ContractService contractService, ExportService exportService) {
        this.contractService = contractService;
        this.exportService = exportService;
    }

    /**
     * Retrieves all contracts.
     *
     * @return a list of {@link ContractDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<ContractDTO>> getAllContracts() {
        List<ContractDTO> contracts = contractService.getAllContracts();
        return ResponseEntity.ok(contracts);
    }

    /**
     * Retrieves a contract by its ID.
     *
     * @param id the contract ID
     * @return the {@link ContractDTO} corresponding to the given ID
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<ContractDTO> getContractById(@PathVariable Long id) {
        ContractDTO contract = contractService.getContractById(id);
        return ResponseEntity.ok(contract);
    }

    /**
     * Retrieves contracts filtered by status.
     *
     * @param status the status to filter contracts (e.g., ACTIVE, EXPIRED,
     * CANCELLED)
     * @return a list of {@link ContractDTO} matching the given status
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/filter")
    public ResponseEntity<List<ContractDTO>> getContractsByStatus(@RequestParam String status) {
        try {
            ContractStatus contractStatus = ContractStatus.valueOf(status.toUpperCase());
            List<ContractDTO> contracts = contractService.getContractsByStatus(contractStatus);
            return ResponseEntity.ok(contracts);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Creates a new contract.
     *
     * @param contractDTO the contract data
     * @return the created {@link ContractDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ContractDTO> createContract(@RequestBody ContractDTO contractDTO) {
        ContractDTO newContract = contractService.createContract(contractDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(newContract);
    }

    /**
     * Updates an existing contract by ID.
     *
     * @param id the contract ID
     * @param contractDTO the updated contract data
     * @return the updated {@link ContractDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ContractDTO> updateContract(@PathVariable Long id, @RequestBody ContractDTO contractDTO) {
        ContractDTO updatedContract = contractService.updateContract(id, contractDTO);
        return ResponseEntity.ok(updatedContract);
    }

    /**
     * Deletes a contract by ID.
     *
     * @param id the contract ID
     * @return HTTP 204 No Content if the contract was successfully deleted
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContract(@PathVariable Long id) {
        contractService.deleteContract(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<ContractStatsResponse> getStats() {
        ContractStatsResponse stats = contractService.getContractStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Retrieves all ACTIVE contracts that will expire within the specified
     * number of days.
     *
     * @param days the number of days to look ahead (default: 30)
     * @return HTTP 200 with a list of expiring contracts
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/expiring")
    public ResponseEntity<List<ContractDTO>> getExpiringContracts(
            @RequestParam(defaultValue = "30") int days
    ) {
        List<ContractDTO> expiring = contractService.getExpiringContracts(days);
        return ResponseEntity.ok(expiring);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/search")
    public ResponseEntity<Page<ContractDTO>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status
    ) {
        ContractStatus st = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                st = ContractStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Invalid status provided; fallback to no status filter
            }
        }
        Page<ContractDTO> result = contractService.searchPaged(q, st, page, size);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/assign-manager")
    public ResponseEntity<Void> assignManager(
            @PathVariable Long id,
            @RequestBody AssignManagerRequest body
    ) {
        contractService.assignManager(id, body.managerId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @GetMapping("/{id}/collaborators")
    public ResponseEntity<List<Long>> getCollaborators(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.getCollaboratorIds(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/collaborators")
    public ResponseEntity<Void> setCollaborators(
            @PathVariable Long id,
            @RequestBody CollaboratorsRequest body) {
        contractService.setCollaborators(id, body.managerIds());
        return ResponseEntity.noContent().build();
    }

    /**
     * Exports all contracts to Excel format (.xlsx).
     *
     * @return Excel file as byte array
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportContractsToExcel() {
        try {
            List<ContractDTO> contracts = contractService.getAllContracts();
            byte[] excelData = exportService.exportContractsToExcel(contracts);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "contracts_export.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);
        } catch (IOException e) {
            logger.error("Failed to export contracts to Excel", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Exports all contracts to PDF format.
     *
     * @return PDF file as byte array
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportContractsToPDF() {
        try {
            List<ContractDTO> contracts = contractService.getAllContracts();
            byte[] pdfData = exportService.exportContractsToPDF(contracts);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "contracts_export.pdf");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfData);
        } catch (DocumentException e) {
            logger.error("Failed to export contracts to PDF", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get contract distribution by business area.
     *
     * @return list of business areas with contract counts
     */
    @GetMapping("/stats/by-area")
    public ResponseEntity<List<ContractsByAreaDTO>> getContractsByArea() {
        List<ContractsByAreaDTO> stats = contractService.getContractsByArea();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get contracts timeline (created per month).
     *
     * @return list of months with contract counts
     */
    @GetMapping("/stats/timeline")
    public ResponseEntity<List<ContractsTimelineDTO>> getContractsTimeline() {
        List<ContractsTimelineDTO> timeline = contractService.getContractsTimeline();
        return ResponseEntity.ok(timeline);
    }

    /**
     * Get top 5 managers by number of assigned contracts.
     *
     * @return list of top managers with contract counts
     */
    @GetMapping("/stats/top-managers")
    public ResponseEntity<List<TopManagerDTO>> getTopManagers() {
        List<TopManagerDTO> topManagers = contractService.getTopManagers();
        return ResponseEntity.ok(topManagers);
    }
}
