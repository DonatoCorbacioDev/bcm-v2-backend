package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.FinancialValueDTO;
import com.donatodev.bcm_backend.service.FinancialValueService;

/**
 * REST controller for managing financial values.
 * <p>
 * Provides endpoints to retrieve, create, update, and delete financial value records.
 */
@RestController
@RequestMapping("/financial-values")
public class FinancialValueController {

	private final FinancialValueService financialValueService;

    public FinancialValueController(FinancialValueService financialValueService) {
        this.financialValueService = financialValueService;
    }

    /**
     * Retrieves all financial values.
     *
     * @return a list of {@link FinancialValueDTO}
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping
    public ResponseEntity<List<FinancialValueDTO>> getAllValues() {
        List<FinancialValueDTO> values = financialValueService.getAllValues();
        return ResponseEntity.ok(values);
    }

    /**
     * Retrieves a specific financial value by its ID.
     *
     * @param id the ID of the financial value
     * @return the {@link FinancialValueDTO}
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<FinancialValueDTO> getValueById(@PathVariable Long id) {
        FinancialValueDTO value = financialValueService.getValueById(id);
        return ResponseEntity.ok(value);
    }

    /**
     * Creates a new financial value entry.
     *
     * @param dto the data of the financial value to be created
     * @return the created {@link FinancialValueDTO}
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping
    public ResponseEntity<FinancialValueDTO> createValue(@RequestBody FinancialValueDTO dto) {
        FinancialValueDTO newValue = financialValueService.createValue(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(newValue);
    }

    /**
     * Updates an existing financial value.
     *
     * @param id  the ID of the financial value to update
     * @param dto the updated data
     * @return the updated {@link FinancialValueDTO}
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<FinancialValueDTO> updateValue(@PathVariable Long id, @RequestBody FinancialValueDTO dto) {
        FinancialValueDTO updated = financialValueService.updateValue(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deletes a financial value by its ID.
     * Only accessible by users with ADMIN role.
     *
     * @param id the ID of the financial value to delete
     * @return HTTP 204 No Content on successful deletion
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteValue(@PathVariable Long id) {
        financialValueService.deleteValue(id);
        return ResponseEntity.noContent().build();
    }
}
