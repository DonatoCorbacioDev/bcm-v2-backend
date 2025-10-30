package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.RoleDTO;
import com.donatodev.bcm_backend.service.RoleService;

/**
 * REST controller for managing user roles.
 * <p>
 * Provides endpoints to retrieve, create, update, and delete roles.
 */
@RestController
@RequestMapping("/roles")
public class RoleController {

	private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * Retrieves all roles.
     *
     * @return a list of {@link RoleDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        List<RoleDTO> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roles);
    }

    /**
     * Retrieves a role by its ID.
     *
     * @param id the ID of the role
     * @return the {@link RoleDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<RoleDTO> getRoleById(@PathVariable Long id) {
        RoleDTO role = roleService.getRoleById(id);
        return ResponseEntity.ok(role);
    }

    /**
     * Creates a new role.
     *
     * @param roleDTO the role to create
     * @return the created {@link RoleDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<RoleDTO> createRole(@RequestBody RoleDTO roleDTO) {
        RoleDTO newRole = roleService.createRole(roleDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(newRole);
    }

    /**
     * Updates an existing role.
     *
     * @param id      the ID of the role to update
     * @param roleDTO the updated role data
     * @return the updated {@link RoleDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<RoleDTO> updateRole(@PathVariable Long id, @RequestBody RoleDTO roleDTO) {
        RoleDTO updatedRole = roleService.updateRole(id, roleDTO);
        return ResponseEntity.ok(updatedRole);
    }

    /**
     * Deletes a role by ID.
     *
     * @param id the ID of the role to delete
     * @return HTTP 204 No Content if deletion is successful
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
