package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.FinancialTypeDTO;
import com.donatodev.bcm_backend.service.FinancialTypeService;

/**
 * REST controller for managing financial types.
 * <p>
 * Provides endpoints to retrieve, create, update and delete financial type entries.
 */
@RestController
@RequestMapping("/financial-types")
public class FinancialTypeController {

	private final FinancialTypeService financialTypeService;

    public FinancialTypeController(FinancialTypeService financialTypeService) {
        this.financialTypeService = financialTypeService;
    }

    /**
     * Retrieves all financial types.
     *
     * @return a list of {@link FinancialTypeDTO}
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping
    public ResponseEntity<List<FinancialTypeDTO>> getAllTypes() {
    	List<FinancialTypeDTO> types = financialTypeService.getAllTypes();
        return ResponseEntity.ok(types);
    }

    /**
     * Retrieves a financial type by its ID.
     *
     * @param id the ID of the financial type
     * @return the {@link FinancialTypeDTO}
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<FinancialTypeDTO> getTypeById(@PathVariable Long id) {
        FinancialTypeDTO type = financialTypeService.getTypeById(id);
        return ResponseEntity.ok(type);
    }

    /**
     * Creates a new financial type.
     *
     * @param dto the financial type data
     * @return the created {@link FinancialTypeDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<FinancialTypeDTO> createType(@RequestBody FinancialTypeDTO dto) {
        FinancialTypeDTO newType = financialTypeService.createType(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(newType);
    }

    /**
     * Updates an existing financial type.
     *
     * @param id  the ID of the financial type to update
     * @param dto the updated data
     * @return the updated {@link FinancialTypeDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<FinancialTypeDTO> updateType(@PathVariable Long id, @RequestBody FinancialTypeDTO dto) {
        FinancialTypeDTO updated = financialTypeService.updateType(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deletes a financial type by ID.
     *
     * @param id the ID of the financial type to delete
     * @return HTTP 204 No Content if deletion is successful
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteType(@PathVariable Long id) {
        financialTypeService.deleteType(id);
        return ResponseEntity.noContent().build();
    }
}
