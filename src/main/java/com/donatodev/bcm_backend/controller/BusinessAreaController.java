package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.BusinessAreaDTO;
import com.donatodev.bcm_backend.service.BusinessAreaService;

/**
 * REST controller for managing Business Areas.
 * <p>
 * Provides endpoints for creating, retrieving, updating and deleting business areas.
 * All endpoints are restricted to users with the ADMIN role.
 */
@RestController
@RequestMapping("business-areas")
public class BusinessAreaController {

	private final BusinessAreaService businessAreaService;

    public BusinessAreaController(BusinessAreaService businessAreaService) {
        this.businessAreaService = businessAreaService;
    }

    /**
     * Retrieves a list of all business areas.
     *
     * @return a list of {@link BusinessAreaDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<BusinessAreaDTO>> getAllAreas() {
        List<BusinessAreaDTO> areas = businessAreaService.getAllAreas();
        return ResponseEntity.ok(areas);
    }

    /**
     * Retrieves a single business area by its ID.
     *
     * @param id the ID of the business area
     * @return the {@link BusinessAreaDTO} with the specified ID
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<BusinessAreaDTO> getAreaById(@PathVariable Long id) {
        BusinessAreaDTO area = businessAreaService.getAreaById(id);
        return ResponseEntity.ok(area);
    }

    /**
     * Creates a new business area.
     *
     * @param dto the data for the new business area
     * @return the created {@link BusinessAreaDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<BusinessAreaDTO> createArea(@RequestBody BusinessAreaDTO dto) {
        BusinessAreaDTO newArea = businessAreaService.createArea(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(newArea);
    }

    /**
     * Updates an existing business area by its ID.
     *
     * @param id  the ID of the business area to update
     * @param dto the updated data
     * @return the updated {@link BusinessAreaDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<BusinessAreaDTO> updateArea(@PathVariable Long id, @RequestBody BusinessAreaDTO dto) {
        BusinessAreaDTO updatedArea = businessAreaService.updateArea(id, dto);
        return ResponseEntity.ok(updatedArea);
    }

    /**
     * Deletes a business area by its ID.
     *
     * @param id the ID of the business area to delete
     * @return HTTP 204 No Content if successful
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArea(@PathVariable Long id) {
        businessAreaService.deleteArea(id);
        return ResponseEntity.noContent().build();
    }
}
