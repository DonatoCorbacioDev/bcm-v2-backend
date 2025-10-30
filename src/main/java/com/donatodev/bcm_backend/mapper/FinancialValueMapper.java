package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.FinancialValueDTO;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;

/**
 * Mapper class responsible for converting between {@link FinancialValues} entities
 * and {@link FinancialValueDTO} data transfer objects.
 * <p>
 * This layer ensures clean separation between the persistence model and
 * the data exposed via the API.
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
    public FinancialValueDTO toDTO(FinancialValues value) {
        return new FinancialValueDTO(
                value.getId(),
                value.getMonth(),
                value.getYear(),
                value.getFinancialAmount(),
                value.getFinancialType().getId(),
                value.getBusinessArea().getId(),
                value.getContract().getId()
        );
    }

    /**
     * Converts a {@link FinancialValueDTO} to a {@link FinancialValues} entity.
     * <p>
     * This method also retrieves related entities (type, area, contract) from the database.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     * @throws RuntimeException if any referenced entity is not found
     */
    public FinancialValues toEntity(FinancialValueDTO dto) {
        return FinancialValues.builder()
                .id(dto.id())
                .month(dto.month())
                .year(dto.year())
                .financialAmount(dto.financialAmount())
                .financialType(financialTypesRepository.findById(dto.financialTypeId())
                        .orElseThrow(() -> new RuntimeException("Financial type not found")))
                .businessArea(businessAreaRepository.findById(dto.businessAreaId())
                        .orElseThrow(() -> new RuntimeException("Business area not found")))
                .contract(contractsRepository.findById(dto.contractId())
                        .orElseThrow(() -> new RuntimeException("Contract not found")))
                .build();
    }
}
