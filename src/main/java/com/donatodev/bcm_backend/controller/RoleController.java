/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.controller;

import java.util.UUID;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.RoleDTO;
import com.donatodev.bcm_backend.service.RoleService;

/**
 * REST controller for managing user roles.
 * <p>
 * Read-only on purpose: ADMIN and MANAGER are shared by every organization,
 * so letting one tenant's admin create, rename or delete a role would change
 * permissions in all the others (renaming MANAGER to ADMIN would promote
 * every manager everywhere). Roles change only through a Flyway migration.
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
    public ResponseEntity<RoleDTO> getRoleById(@PathVariable UUID id) {
        RoleDTO role = roleService.getRoleById(id);
        return ResponseEntity.ok(role);
    }
}
