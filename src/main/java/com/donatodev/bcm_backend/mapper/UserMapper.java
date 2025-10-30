package com.donatodev.bcm_backend.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.exception.RoleNotFoundException;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;

/**
 * Mapper class responsible for converting between {@link Users} entities
 * and {@link UserDTO} data transfer objects.
 * <p>
 * This component handles the transformation logic for user-related data
 * and manages references to roles and managers using repositories.
 */
@Component
public class UserMapper {
	
	private static final Logger logger = LoggerFactory.getLogger(UserMapper.class);

	private final ManagersRepository managersRepository;
    private final RolesRepository rolesRepository;

    public UserMapper(ManagersRepository managersRepository, RolesRepository rolesRepository) {
        this.managersRepository = managersRepository;
        this.rolesRepository = rolesRepository;
    }

    /**
     * Converts a {@link Users} entity to a {@link UserDTO}.
     * <p>
     * The password is not included in the output for security reasons.
     *
     * @param user the user entity
     * @return the corresponding DTO
     */
    public UserDTO toDTO(Users user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                null, // Do not expose the hashed password
                user.getManager().getId(),
                user.getRole().getId()
        );
    }

    /**
     * Converts a {@link UserDTO} to a {@link Users} entity.
     * <p>
     * This method fetches the referenced {@link Managers} and {@link Roles} entities.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     * @throws ManagerNotFoundException if the manager ID is provided but not found
     * @throws RoleNotFoundException if the role ID is not found
     */
    public Users toEntity(UserDTO dto) {
    	logger.info(">>> toEntity: managerId = {}, roleId = {}", dto.managerId(), dto.roleId());

        Managers manager = null;
        if (dto.managerId() != null) {
            manager = managersRepository.findById(dto.managerId())
                .orElseThrow(() -> new ManagerNotFoundException("Manager ID " + dto.managerId() + " not found"));
        }

        Roles role = rolesRepository.findById(dto.roleId())
            .orElseThrow(() -> new RoleNotFoundException("Role ID " + dto.roleId() + " not found"));

        return Users.builder()
            .id(dto.id())
            .username(dto.username())
            .passwordHash(dto.password())
            .manager(manager)
            .role(role)
            .build();
    }
}
