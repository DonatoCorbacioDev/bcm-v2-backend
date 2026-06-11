package com.donatodev.bcm_backend.service;

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
        Long orgId = TenantContext.get();
        List<FinancialTypes> types = (orgId != null)
                ? financialTypesRepository.findAllByOrganizationId(orgId)
                : financialTypesRepository.findAll();
        return types.stream().map(financialTypeMapper::toDTO).toList();
    }

    /**
     * Retrieves a financial type by its ID.
     *
     * @param id the ID of the financial type
     * @return the corresponding {@link FinancialTypeDTO}
     * @throws FinancialTypeNotFoundException if the financial type is not found
     */
    public FinancialTypeDTO getTypeById(Long id) {
        return findTypeInScope(id)
                .map(financialTypeMapper::toDTO)
                .orElseThrow(() -> new FinancialTypeNotFoundException("Financial type ID " + id + " not found"));
    }

    /**
     * Finds a financial type by ID, scoped to the current tenant when
     * {@link TenantContext} carries an organization ID.
     */
    private Optional<FinancialTypes> findTypeInScope(Long id) {
        Long orgId = TenantContext.get();
        return (orgId != null)
                ? financialTypesRepository.findByIdAndOrganizationId(id, orgId)
                : financialTypesRepository.findById(id);
    }

    /**
     * Creates a new financial type.
     *
     * @param dto the financial type data transfer object
     * @return the created {@link FinancialTypeDTO}
     */
    public FinancialTypeDTO createType(FinancialTypeDTO dto) {
        FinancialTypes type = financialTypeMapper.toEntity(dto);
        Long orgId = TenantContext.get();
        if (orgId != null) {
            Organization org = new Organization();
            org.setId(orgId);
            type.setOrganization(org);
        }
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
    public FinancialTypeDTO updateType(Long id, FinancialTypeDTO dto) {
        FinancialTypes type = findTypeInScope(id)
                .orElseThrow(() -> new FinancialTypeNotFoundException("Financial type ID " + id + " not found"));

        type.setName(dto.name());
        type.setDescription(dto.description());

        type = financialTypesRepository.save(type);
        return financialTypeMapper.toDTO(type);
    }

    /**
     * Deletes a financial type by its ID.
     *
     * @param id the ID of the financial type to delete
     * @throws FinancialTypeNotFoundException if the financial type is not found
     */
    public void deleteType(Long id) {
        FinancialTypes type = findTypeInScope(id)
                .orElseThrow(() -> new FinancialTypeNotFoundException("Financial type ID " + id + " not found"));
        financialTypesRepository.delete(type);
    }
}
