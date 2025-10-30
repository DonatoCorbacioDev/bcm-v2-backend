package com.donatodev.bcm_backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.RoleDTO;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.exception.RoleNotFoundException;
import com.donatodev.bcm_backend.mapper.RoleMapper;
import com.donatodev.bcm_backend.repository.RolesRepository;

/**
 * Service class for managing business logic related to roles.
 * <p>
 * Provides methods to retrieve, create, update, and delete roles.
 */
@Service
public class RoleService {

	private final RolesRepository rolesRepository;
    private final RoleMapper roleMapper;

    public RoleService(RolesRepository rolesRepository, RoleMapper roleMapper) {
        this.rolesRepository = rolesRepository;
        this.roleMapper = roleMapper;
    }

    /**
     * Retrieves all roles.
     *
     * @return list of {@link RoleDTO}
     */
    public List<RoleDTO> getAllRoles() {
        return rolesRepository.findAll()
                .stream()
                .map(roleMapper::toDTO)
                .toList();
    }

    /**
     * Retrieves a role by its ID.
     *
     * @param id the ID of the role
     * @return the corresponding {@link RoleDTO}
     * @throws RoleNotFoundException if role not found
     */
    public RoleDTO getRoleById(Long id) {
        return rolesRepository.findById(id)
                .map(roleMapper::toDTO)
                .orElseThrow(() -> new RoleNotFoundException("Role ID " + id + " not found"));
    }

    /**
     * Creates a new role.
     *
     * @param dto the role data transfer object
     * @return the created {@link RoleDTO}
     */
    public RoleDTO createRole(RoleDTO dto) {
        Roles role = roleMapper.toEntity(dto);
        role = rolesRepository.save(role);
        return roleMapper.toDTO(role);
    }

    /**
     * Updates an existing role.
     *
     * @param id  the ID of the role to update
     * @param dto the role data transfer object with updated data
     * @return the updated {@link RoleDTO}
     * @throws RoleNotFoundException if role not found
     */
    public RoleDTO updateRole(Long id, RoleDTO dto) {
        Roles role = rolesRepository.findById(id)
                .orElseThrow(() -> new RoleNotFoundException("Role ID " + id + " not found"));

        role.setRole(dto.role());
        role = rolesRepository.save(role);
        return roleMapper.toDTO(role);
    }

    /**
     * Deletes a role by its ID.
     *
     * @param id the ID of the role to delete
     */
    public void deleteRole(Long id) {
        rolesRepository.deleteById(id);
    }
}
