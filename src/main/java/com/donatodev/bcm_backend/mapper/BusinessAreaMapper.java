package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.BusinessAreaDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;

/**
 * Mapper class responsible for converting between {@link BusinessAreas} entities
 * and {@link BusinessAreaDTO} data transfer objects.
 * <p>
 * This layer helps decouple the persistence layer from the API layer,
 * ensuring that only relevant data is exposed externally.
 */
@Component
public class BusinessAreaMapper {

    /**
     * Converts a {@link BusinessAreas} entity to a {@link BusinessAreaDTO}.
     *
     * @param area the business area entity to convert
     * @return the corresponding DTO
     */
    public BusinessAreaDTO toDTO(BusinessAreas area) {
        return new BusinessAreaDTO(
                area.getId(),
                area.getName(),
                area.getDescription()
        );
    }

    /**
     * Converts a {@link BusinessAreaDTO} to a {@link BusinessAreas} entity.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     */
    public BusinessAreas toEntity(BusinessAreaDTO dto) {
        return BusinessAreas.builder()
                .id(dto.id())
                .name(dto.name())
                .description(dto.description())
                .build();
    }
}
