/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.FinancialTypeDTO;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.exception.FinancialTypeNotFoundException;
import com.donatodev.bcm_backend.mapper.FinancialTypeMapper;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;

/**
 * Service class managing business logic related to financial types.
 * <p>
 * Provides methods to retrieve, create, update, and delete financial types.
 */
@Service
public class FinancialTypeService {

	private static final String FINANCIAL_TYPE_ID_PREFIX = "ID tipo finanziario ";
	private static final String NOT_FOUND_SUFFIX = " non trovato";

	private final FinancialTypesRepository financialTypesRepository;
    private final FinancialTypeMapper financialTypeMapper;

    public FinancialTypeService(FinancialTypesRepository financialTypesRepository,
                               FinancialTypeMapper financialTypeMapper) {
        this.financialTypesRepository = financialTypesRepository;
        this.financialTypeMapper = financialTypeMapper;
    }

    /**
     * Retrieves all financial types.
     *
     * @return list of {@link FinancialTypeDTO}
     */
    public List<FinancialTypeDTO> getAllTypes() {
        UUID orgId = TenantContext.require();
        List<FinancialTypes> types = financialTypesRepository.findAllByOrganizationId(orgId);
        return types.stream().map(financialTypeMapper::toDTO).toList();
    }

    /**
     * Retrieves a financial type by its ID.
     *
     * @param id the ID of the financial type
     * @return the corresponding {@link FinancialTypeDTO}
     * @throws FinancialTypeNotFoundException if the financial type is not found
     */
    public FinancialTypeDTO getTypeById(UUID id) {
        return findTypeInScope(id)
                .map(financialTypeMapper::toDTO)
                .orElseThrow(() -> new FinancialTypeNotFoundException(FINANCIAL_TYPE_ID_PREFIX + id + NOT_FOUND_SUFFIX));
    }

    /**
     * Finds a financial type by ID, scoped to the current tenant when
     * {@link TenantContext} carries an organization ID.
     */
    private Optional<FinancialTypes> findTypeInScope(UUID id) {
        UUID orgId = TenantContext.require();
        return financialTypesRepository.findByIdAndOrganizationId(id, orgId);
    }

    /**
     * Creates a new financial type.
     *
     * @param dto the financial type data transfer object
     * @return the created {@link FinancialTypeDTO}
     */
    public FinancialTypeDTO createType(FinancialTypeDTO dto) {
        FinancialTypes type = financialTypeMapper.toEntity(dto);
        // Creation must never merge an existing row selected by a client-supplied ID.
        type.setId(null);
        UUID orgId = TenantContext.require();
        Organization org = new Organization();
        org.setId(orgId);
        type.setOrganization(org);
        type = financialTypesRepository.save(type);
        return financialTypeMapper.toDTO(type);
    }

    /**
     * Updates an existing financial type.
     *
     * @param id  the ID of the financial type to update
     * @param dto the financial type data transfer object containing updated data
     * @return the updated {@link FinancialTypeDTO}
     * @throws FinancialTypeNotFoundException if the financial type is not found
     */
    public FinancialTypeDTO updateType(UUID id, FinancialTypeDTO dto) {
        FinancialTypes type = findTypeInScope(id)
                .orElseThrow(() -> new FinancialTypeNotFoundException(FINANCIAL_TYPE_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        type.setName(dto.name());
        type.setDescription(dto.description());
        type.setCategory(dto.category());

        type = financialTypesRepository.save(type);
        return financialTypeMapper.toDTO(type);
    }

    /**
     * Deletes a financial type by its ID.
     *
     * @param id the ID of the financial type to delete
     * @throws FinancialTypeNotFoundException if the financial type is not found
     */
    public void deleteType(UUID id) {
        FinancialTypes type = findTypeInScope(id)
                .orElseThrow(() -> new FinancialTypeNotFoundException(FINANCIAL_TYPE_ID_PREFIX + id + NOT_FOUND_SUFFIX));
        financialTypesRepository.delete(type);
    }
}
