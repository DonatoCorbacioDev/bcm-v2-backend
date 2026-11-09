/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.UUID;
import java.util.List;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.RoleDTO;
import com.donatodev.bcm_backend.exception.RoleNotFoundException;
import com.donatodev.bcm_backend.mapper.RoleMapper;
import com.donatodev.bcm_backend.repository.RolesRepository;

/**
 * Service class for managing business logic related to roles.
 * <p>
 * Read-only: roles are shared by every organization (see RoleController).
 */
@Service
public class RoleService {

	private final RolesRepository rolesRepository;
    private final RoleMapper roleMapper;

    public RoleService(RolesRepository rolesRepository, RoleMapper roleMapper) {
        this.rolesRepository = rolesRepository;
        this.roleMapper = roleMapper;
    }

    /**
     * Retrieves all roles.
     *
     * @return list of {@link RoleDTO}
     */
    public List<RoleDTO> getAllRoles() {
        return rolesRepository.findAll()
                .stream()
                .map(roleMapper::toDTO)
                .toList();
    }

    /**
     * Retrieves a role by its ID.
     *
     * @param id the ID of the role
     * @return the corresponding {@link RoleDTO}
     * @throws RoleNotFoundException if role not found
     */
    public RoleDTO getRoleById(UUID id) {
        return rolesRepository.findById(id)
                .map(roleMapper::toDTO)
                .orElseThrow(() -> new RoleNotFoundException("Ruolo ID " + id + " non trovato"));
    }
}
