package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.ContractHistoryDTO;
import com.donatodev.bcm_backend.service.ContractHistoryService;

/**
 * REST controller for managing contract history.
 * <p>
 * Provides endpoints for creating, retrieving and deleting historical contract records.
 * Access is controlled by user roles.
 */
@RestController
@RequestMapping("/contract-history")
public class ContractHistoryController {

	private final ContractHistoryService contractHistoryService;

    public ContractHistoryController(ContractHistoryService contractHistoryService) {
        this.contractHistoryService = contractHistoryService;
    }

    /**
     * Retrieves all contract history records.
     *
     * @return a list of {@link ContractHistoryDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<ContractHistoryDTO>> getAll() {
        return ResponseEntity.ok(contractHistoryService.getAll());
    }

    /**
     * Retrieves contract history records by contract ID.
     *
     * @param contractId the ID of the contract
     * @return a list of {@link ContractHistoryDTO} related to the contract
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/contract/{contractId}")
    public ResponseEntity<List<ContractHistoryDTO>> getByContractId(@PathVariable Long contractId) {
        return ResponseEntity.ok(contractHistoryService.getByContractId(contractId));
    }

    /**
     * Retrieves a single contract history record by its ID.
     *
     * @param id the ID of the contract history record
     * @return the {@link ContractHistoryDTO}
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<ContractHistoryDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(contractHistoryService.getById(id));
    }

    /**
     * Creates a new contract history record.
     *
     * @param dto the contract history data
     * @return the created {@link ContractHistoryDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ContractHistoryDTO> create(@RequestBody ContractHistoryDTO dto) {
        ContractHistoryDTO newHistory = contractHistoryService.create(dto);
        return ResponseEntity.status(201).body(newHistory);
    }

    /**
     * Deletes a contract history record by ID.
     *
     * @param id the ID of the record to delete
     * @return HTTP 204 No Content if deletion was successful
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contractHistoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
