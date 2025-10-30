package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.RoleDTO;
import com.donatodev.bcm_backend.entity.Roles;

/**
 * Mapper class responsible for converting between {@link Roles} entities
 * and {@link RoleDTO} data transfer objects.
 * <p>
 * This component is used to isolate and centralize the logic for transforming
 * role-related data between the persistence and API layers.
 */
@Component
public class RoleMapper {

    /**
     * Converts a {@link Roles} entity to a {@link RoleDTO}.
     *
     * @param role the role entity to convert
     * @return the corresponding DTO
     */
    public RoleDTO toDTO(Roles role) {
        return new RoleDTO(
                role.getId(),
                role.getRole()
        );
    }

    /**
     * Converts a {@link RoleDTO} to a {@link Roles} entity.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     */
    public Roles toEntity(RoleDTO dto) {
        return Roles.builder()
                .id(dto.id())
                .role(dto.role())
                .build();
    }
}
