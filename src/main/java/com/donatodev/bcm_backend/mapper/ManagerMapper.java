package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.ManagerDTO;
import com.donatodev.bcm_backend.entity.Managers;

/**
 * Mapper class responsible for converting between {@link Managers} entities
 * and {@link ManagerDTO} data transfer objects.
 * <p>
 * This layer isolates the persistence model from the API layer,
 * providing a clear and consistent transformation logic.
 */
@Component
public class ManagerMapper {

    /**
     * Converts a {@link Managers} entity to a {@link ManagerDTO}.
     *
     * @param manager the manager entity to convert
     * @return the corresponding DTO
     */
    public ManagerDTO toDTO(Managers manager) {
        return new ManagerDTO(
                manager.getId(),
                manager.getFirstName(),
                manager.getLastName(),
                manager.getEmail(),
                manager.getPhoneNumber(),
                manager.getDepartment()
        );
    }

    /**
     * Converts a {@link ManagerDTO} to a {@link Managers} entity.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     */
    public Managers toEntity(ManagerDTO dto) {
        return Managers.builder()
                .id(dto.id())
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .email(dto.email())
                .phoneNumber(dto.phoneNumber())
                .department(dto.department())
                .build();
    }
}
