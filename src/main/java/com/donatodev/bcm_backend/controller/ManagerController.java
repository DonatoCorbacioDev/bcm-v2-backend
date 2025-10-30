package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.ManagerDTO;
import com.donatodev.bcm_backend.service.ManagerService;

/**
 * REST controller for managing company managers.
 * <p>
 * Provides endpoints to retrieve, create, update, and delete manager records.
 */
@RestController
@RequestMapping("/managers")
public class ManagerController {

	private final ManagerService managerService;

    public ManagerController(ManagerService managerService) {
        this.managerService = managerService;
    }

    /**
     * Retrieves all managers.
     *
     * @return a list of {@link ManagerDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<ManagerDTO>> getAllManagers() {
        List<ManagerDTO> managers = managerService.getAllManagers();
        return ResponseEntity.ok(managers);
    }

    /**
     * Retrieves a manager by ID.
     *
     * @param id the ID of the manager
     * @return the {@link ManagerDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ManagerDTO> getManagerById(@PathVariable Long id) {
        ManagerDTO manager = managerService.getManagerById(id);
        return ResponseEntity.ok(manager);
    }

    /**
     * Creates a new manager.
     *
     * @param managerDTO the manager data
     * @return the created {@link ManagerDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ManagerDTO> createManager(@RequestBody ManagerDTO managerDTO) {
        ManagerDTO newManager = managerService.createManager(managerDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(newManager);
    }

    /**
     * Updates an existing manager.
     *
     * @param id         the ID of the manager to update
     * @param managerDTO the updated data
     * @return the updated {@link ManagerDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("{id}")
    public ResponseEntity<ManagerDTO> updateManager(@PathVariable Long id, @RequestBody ManagerDTO managerDTO) {
        ManagerDTO updatedManager = managerService.updateManager(id, managerDTO);
        return ResponseEntity.ok(updatedManager);
    }

    /**
     * Deletes a manager by ID.
     *
     * @param id the ID of the manager to delete
     * @return HTTP 204 No Content if deletion is successful
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteManager(@PathVariable Long id) {
        managerService.deleteManager(id);
        return ResponseEntity.noContent().build();
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<Page<ManagerDTO>> searchManagers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q
    ) {
        Page<ManagerDTO> res = managerService.searchManagers(q, page, size);
        return ResponseEntity.ok(res);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ManagerDTO> patchManager(@PathVariable Long id, @RequestBody ManagerDTO managerDTO) {
        ManagerDTO updated = managerService.updateManager(id, managerDTO);
        return ResponseEntity.ok(updated);
    }

}
