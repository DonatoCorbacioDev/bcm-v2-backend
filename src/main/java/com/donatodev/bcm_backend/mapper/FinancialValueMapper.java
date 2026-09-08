/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.FinancialValueDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.FinancialValueSource;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.exception.BusinessAreaNotFoundException;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.FinancialTypeNotFoundException;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;

/**
 * Mapper class responsible for converting between {@link FinancialValues}
 * entities and {@link FinancialValueDTO} data transfer objects.
 * <p>
 * This layer ensures clean separation between the persistence model and the
 * data exposed via the API.
 */
@Component
public class FinancialValueMapper {

    private final FinancialTypesRepository financialTypesRepository;
    private final BusinessAreasRepository businessAreaRepository;
    private final ContractsRepository contractsRepository;

    public FinancialValueMapper(
            FinancialTypesRepository financialTypesRepository,
            BusinessAreasRepository businessAreaRepository,
            ContractsRepository contractsRepository) {
        this.financialTypesRepository = financialTypesRepository;
        this.businessAreaRepository = businessAreaRepository;
        this.contractsRepository = contractsRepository;
    }

    /**
     * Converts a {@link FinancialValues} entity to a {@link FinancialValueDTO}.
     *
     * @param value the financial value entity
     * @return the corresponding DTO
     */
    public FinancialValueDTO toDTO(FinancialValues entity) {
        if (entity == null) {
            return null;
        }

        return new FinancialValueDTO(
                entity.getId(),
                entity.getMonth(),
                entity.getYear(),
                entity.getFinancialAmount(),
                entity.getFinancialType() != null ? entity.getFinancialType().getId() : null,
                entity.getBusinessArea() != null ? entity.getBusinessArea().getId() : null,
                entity.getContract() != null ? entity.getContract().getId() : null,
                entity.getFinancialType() != null ? entity.getFinancialType().getName() : null,
                entity.getBusinessArea() != null ? entity.getBusinessArea().getName() : null,
                entity.getContract() != null ? entity.getContract().getCounterparty().getName() : null,
                entity.getFinancialType() != null ? entity.getFinancialType().getCategory() : null,
                entity.getSource()
        );
    }

    /**
     * Updates an existing {@link FinancialValues} entity in-place from a
     * {@link FinancialValueDTO}, resolving all relation changes via repositories.
     * Every relation is re-resolved scoped to {@code orgId} — see
     * {@link #resolveContract}, {@link #resolveFinancialType}, {@link #resolveBusinessArea}
     * for why this can't be a plain unscoped lookup by ID.
     * <p>
     * Always sets {@code source} to {@code MANUAL}, ignoring whatever the DTO
     * carries — this mapper backs only the manual CRUD path (see
     * {@code FinancialValueService}), so editing a row by hand always "claims"
     * it from generation, even if it was previously {@code GENERATED}.
     *
     * @param existing the entity to update
     * @param dto      the DTO with the new values
     * @param orgId    the caller's organization, or {@code null} outside an
     *                 authenticated HTTP request (see the scoped resolve helpers)
     */
    public void updateEntity(FinancialValues existing, FinancialValueDTO dto, Long orgId) {
        existing.setMonth(dto.month());
        existing.setYear(dto.year());
        existing.setFinancialAmount(dto.financialAmount());
        existing.setFinancialType(resolveFinancialType(dto.financialTypeId(), orgId));
        existing.setBusinessArea(resolveBusinessArea(dto.businessAreaId(), orgId));
        existing.setContract(resolveContract(dto.contractId(), orgId));
        existing.setSource(FinancialValueSource.MANUAL);
    }

    /**
     * Converts a {@link FinancialValueDTO} to a {@link FinancialValues} entity.
     * <p>
     * This method also retrieves related entities (type, area, contract) from
     * the database, each scoped to {@code orgId} (see the scoped resolve helpers) —
     * a financialTypeId/businessAreaId/contractId belonging to another
     * organization must never attach to this value.
     *
     * @param dto   the DTO to convert
     * @param orgId the caller's organization, or {@code null} outside an
     *              authenticated HTTP request
     * @return the corresponding entity
     */
    public FinancialValues toEntity(FinancialValueDTO dto, Long orgId) {
        return FinancialValues.builder()
                .id(dto.id())
                .month(dto.month())
                .year(dto.year())
                .financialAmount(dto.financialAmount())
                .financialType(resolveFinancialType(dto.financialTypeId(), orgId))
                .businessArea(resolveBusinessArea(dto.businessAreaId(), orgId))
                .contract(resolveContract(dto.contractId(), orgId))
                // This mapper backs only the manual CRUD path — see updateEntity's Javadoc.
                .source(FinancialValueSource.MANUAL)
                .build();
    }

    // orgId is null only outside a real HTTP request (e.g. tests using
    // @WithMockUser without the JWT filter) — same fallback convention used
    // throughout the service layer (see ContractService.findContractInScope).
    // Whenever orgId is present, the lookup MUST be scoped: these three IDs
    // come straight from client input, and an unscoped findById would let a
    // caller attach another organization's contract/type/area to their own
    // financial value (real, exploitable cross-tenant leak — see the
    // anomaly-detection join in bcm-v2-ml, which trusts this association).

    private Contracts resolveContract(Long id, Long orgId) {
        return (orgId != null
                ? contractsRepository.findByIdAndOrganization_Id(id, orgId)
                : contractsRepository.findById(id))
                .orElseThrow(() -> new ContractNotFoundException("Contratto ID " + id + " non trovato"));
    }

    private FinancialTypes resolveFinancialType(Long id, Long orgId) {
        return (orgId != null
                ? financialTypesRepository.findByIdAndOrganizationId(id, orgId)
                : financialTypesRepository.findById(id))
                .orElseThrow(() -> new FinancialTypeNotFoundException("Tipo finanziario ID " + id + " non trovato"));
    }

    private BusinessAreas resolveBusinessArea(Long id, Long orgId) {
        return (orgId != null
                ? businessAreaRepository.findByIdAndOrganizationId(id, orgId)
                : businessAreaRepository.findById(id))
                .orElseThrow(() -> new BusinessAreaNotFoundException("Area di business ID " + id + " non trovata"));
    }
}
