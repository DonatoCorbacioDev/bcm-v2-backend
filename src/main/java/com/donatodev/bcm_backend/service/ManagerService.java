/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ManagerDTO;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.auth.ReauthenticationService;
import com.donatodev.bcm_backend.mapper.ManagerMapper;
import com.donatodev.bcm_backend.repository.ManagersRepository;

/**
 * Service class for managing business logic related to managers.
 * <p>
 * Provides methods to retrieve, create, update, and delete managers.
 */
@Service
public class ManagerService {

    private static final String MANAGER_ID_PREFIX = "ID manager ";
	private static final String NOT_FOUND_SUFFIX = " non trovato";


	private final ManagersRepository managersRepository;
    private final ManagerMapper managerMapper;
    private final ReauthenticationService reauthenticationService;

    public ManagerService(ManagersRepository managersRepository, ManagerMapper managerMapper,
            ReauthenticationService reauthenticationService) {
        this.managersRepository = managersRepository;
        this.managerMapper = managerMapper;
        this.reauthenticationService = reauthenticationService;
    }

    /**
     * Retrieves all managers.
     *
     * @return a list of {@link ManagerDTO}
     */
    public List<ManagerDTO> getAllManagers() {
        UUID orgId = TenantContext.require();
        boolean admin = callerIsAdmin();
        return managersRepository.findByOrganizationId(orgId, Pageable.unpaged())
                .stream().map(managerMapper::toDTO)
                .map(dto -> admin ? dto : withoutContacts(dto)).toList();
    }

    /**
     * A manager sees its colleagues by name, not their email and phone: those
     * are an administrator's (security audit 2026-11-24; on the public demo the
     * list was readable by anyone with its published login).
     */
    private static ManagerDTO withoutContacts(ManagerDTO dto) {
        return new ManagerDTO(dto.id(), dto.firstName(), dto.lastName(), null, null, dto.department());
    }

    /** The role loaded from the database for this request (CustomUserDetailsService). */
    private static boolean callerIsAdmin() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Retrieves a manager by their ID.
     *
     * @param id the ID of the manager
     * @return the corresponding {@link ManagerDTO}
     * @throws ManagerNotFoundException if manager not found
     */
    public ManagerDTO getManagerById(UUID id) {
        return findManagerInScope(id)
                .map(managerMapper::toDTO)
                .orElseThrow(() -> new ManagerNotFoundException(MANAGER_ID_PREFIX + id + NOT_FOUND_SUFFIX));
    }

    /**
     * Finds a manager by ID, scoped to the current tenant when
     * {@link TenantContext} carries an organization ID.
     */
    private Optional<Managers> findManagerInScope(UUID id) {
        UUID orgId = TenantContext.require();
        return managersRepository.findByIdAndOrganizationId(id, orgId);
    }

    /**
     * Creates a new manager.
     *
     * @param managerDTO the manager data transfer object
     * @return the created {@link ManagerDTO}
     */
    @Transactional
    public ManagerDTO createManager(ManagerDTO managerDTO) {
        Managers manager = managerMapper.toEntity(managerDTO);
        // Creation must never merge an existing row selected by a client-supplied ID.
        manager.setId(null);
        UUID orgId = TenantContext.require();
        Organization org = new Organization();
        org.setId(orgId);
        manager.setOrganization(org);
        manager = managersRepository.save(manager);
        return managerMapper.toDTO(manager);
    }

    /**
     * Updates an existing manager.
     *
     * @param id         the ID of the manager to update
     * @param managerDTO the updated manager data transfer object
     * @return the updated {@link ManagerDTO}
     * @throws ManagerNotFoundException if manager not found
     */
    @Transactional
    public ManagerDTO updateManager(UUID id, ManagerDTO managerDTO) {
        Managers manager = findManagerInScope(id)
                .orElseThrow(() -> new ManagerNotFoundException(MANAGER_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        // The address password resets are sent to: changed only after the
        // password again (OWASP ASVS 7.5.1), or a stolen session could point it
        // at its own mailbox and reset the account's password.
        if (managerDTO.email() != null && !managerDTO.email().equalsIgnoreCase(manager.getEmail())) {
            reauthenticationService.require();
        }
        manager.setFirstName(managerDTO.firstName());
        manager.setLastName(managerDTO.lastName());
        manager.setEmail(managerDTO.email());
        manager.setPhoneNumber(managerDTO.phoneNumber());
        manager.setDepartment(managerDTO.department());

        manager = managersRepository.save(manager);
        return managerMapper.toDTO(manager);
    }

    /**
     * Deletes a manager by their ID.
     *
     * @param id the ID of the manager to delete
     * @throws ManagerNotFoundException if manager not found
     */
    @Transactional
    public void deleteManager(UUID id) {
        Managers manager = findManagerInScope(id)
                .orElseThrow(() -> new ManagerNotFoundException(MANAGER_ID_PREFIX + id + NOT_FOUND_SUFFIX));
        managersRepository.delete(manager);
    }
    
    public Managers getManagerEntity(UUID id) {
        return findManagerInScope(id)
            .orElseThrow(() -> new ManagerNotFoundException(MANAGER_ID_PREFIX + id + NOT_FOUND_SUFFIX));
    }
}