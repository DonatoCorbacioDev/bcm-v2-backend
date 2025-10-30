package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.FinancialTypeDTO;
import com.donatodev.bcm_backend.entity.FinancialTypes;

/**
 * Mapper class responsible for converting between {@link FinancialTypes} entities
 * and {@link FinancialTypeDTO} data transfer objects.
 * <p>
 * This layer isolates persistence logic from the API layer by controlling
 * how financial type data is transformed and exposed.
 */
@Component
public class FinancialTypeMapper {

    /**
     * Converts a {@link FinancialTypes} entity to a {@link FinancialTypeDTO}.
     *
     * @param type the financial type entity
     * @return the corresponding DTO
     */
    public FinancialTypeDTO toDTO(FinancialTypes type) {
        return new FinancialTypeDTO(
                type.getId(),
                type.getName(),
                type.getDescription()
        );
    }

    /**
     * Converts a {@link FinancialTypeDTO} to a {@link FinancialTypes} entity.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     */
    public FinancialTypes toEntity(FinancialTypeDTO dto) {
        return FinancialTypes.builder()
                .id(dto.id())
                .name(dto.name())
                .description(dto.description())
                .build();
    }
}
