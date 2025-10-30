package com.donatodev.bcm_backend.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.RoleDTO;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.exception.RoleNotFoundException;
import com.donatodev.bcm_backend.mapper.RoleMapper;
import com.donatodev.bcm_backend.repository.RolesRepository;

/**
 * Unit tests for {@link RoleService}.
 * <p>
 * This test class verifies the business logic of role management, including:
 * <ul>
 *   <li>Fetching all roles</li>
 *   <li>Retrieving a role by ID</li>
 *   <li>Creating new roles</li>
 *   <li>Updating existing roles</li>
 *   <li>Deleting roles</li>
 * </ul>
 * It uses {@code Mockito} for mocking dependencies and ensures proper exception handling
 * via {@link com.donatodev.bcm_backend.exception.RoleNotFoundException}.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class RoleServiceTest {

    @Mock
    private RolesRepository rolesRepository;

    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private RoleService roleService;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: RoleService")
    @SuppressWarnings("unused")
    class VerifyRoleService {

    	/**
    	 * Verifies that a list of RoleDTOs is correctly returned from the repository.
    	 */
        @Test
        @Order(1)
        @DisplayName("Get all roles returns list of DTOs")
        void shouldGetAllRoles() {
            Roles entity = Roles.builder().id(1L).role("ADMIN").build();
            RoleDTO dto = new RoleDTO(1L, "ADMIN");

            when(rolesRepository.findAll()).thenReturn(List.of(entity));
            when(roleMapper.toDTO(entity)).thenReturn(dto);

            List<RoleDTO> result = roleService.getAllRoles();

            assertEquals(1, result.size());
            assertEquals("ADMIN", result.get(0).role());
        }

        /**
         * Verifies that the correct RoleDTO is returned for a valid ID.
         */
        @Test
        @Order(2)
        @DisplayName("Get role by ID returns DTO")
        void shouldGetRoleById() {
            Roles entity = Roles.builder().id(1L).role("MANAGER").build();
            RoleDTO dto = new RoleDTO(1L, "MANAGER");

            when(rolesRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(roleMapper.toDTO(entity)).thenReturn(dto);

            RoleDTO result = roleService.getRoleById(1L);

            assertEquals("MANAGER", result.role());
        }

        /**
         * Verifies that {@link com.donatodev.bcm_backend.exception.RoleNotFoundException}
         * is thrown when the role ID is not found.
         */
        @Test
        @Order(3)
        @DisplayName("Get role by ID throws exception if not found")
        void shouldThrowIfRoleNotFound() {
            when(rolesRepository.findById(999L)).thenReturn(Optional.empty());
            RoleNotFoundException ex =
                assertThrows(RoleNotFoundException.class, () -> roleService.getRoleById(999L));
            assertEquals("Role ID 999 not found", ex.getMessage());
        }

        /**
         * Verifies that a new role is created and returned as a RoleDTO.
         */
        @Test
        @Order(4)
        @DisplayName("Create role returns saved DTO")
        void shouldCreateRole() {
            RoleDTO dto = new RoleDTO(null, "MANAGER");
            Roles entity = Roles.builder().role("MANAGER").build();
            Roles saved = Roles.builder().id(1L).role("MANAGER").build();
            RoleDTO savedDTO = new RoleDTO(1L, "MANAGER");

            when(roleMapper.toEntity(dto)).thenReturn(entity);
            when(rolesRepository.save(entity)).thenReturn(saved);
            when(roleMapper.toDTO(saved)).thenReturn(savedDTO);

            RoleDTO result = roleService.createRole(dto);

            assertEquals(1L, result.id());
            assertEquals("MANAGER", result.role());
        }

        /**
         * Verifies that an existing role is updated correctly and returned as a RoleDTO.
         */
        @Test
        @Order(5)
        @DisplayName("Update role returns updated DTO")
        void shouldUpdateRole() {
            Roles existing = Roles.builder().id(1L).role("USER").build();
            RoleDTO updateDTO = new RoleDTO(1L, "ADMIN");
            RoleDTO updatedDTO = new RoleDTO(1L, "ADMIN");

            when(rolesRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(rolesRepository.save(existing)).thenReturn(existing);
            when(roleMapper.toDTO(existing)).thenReturn(updatedDTO);

            RoleDTO result = roleService.updateRole(1L, updateDTO);

            assertEquals("ADMIN", result.role());
        }

        /**
         * Verifies that {@link com.donatodev.bcm_backend.exception.RoleNotFoundException}
         * is thrown when trying to update a non-existent role.
         */
        @Test
        @Order(6)
        @DisplayName("Update role throws exception if not found")
        void shouldThrowWhenUpdatingMissingRole() {
            RoleDTO dto = new RoleDTO(1L, "MODERATOR");
            when(rolesRepository.findById(1L)).thenReturn(Optional.empty());

            RoleNotFoundException ex =
                assertThrows(RoleNotFoundException.class, () -> roleService.updateRole(1L, dto));
            assertEquals("Role ID 1 not found", ex.getMessage());
        }

        /**
         * Verifies that the delete method of the repository is called for the given ID.
         */
        @Test
        @Order(7)
        @DisplayName("Delete role calls repository")
        void shouldDeleteRole() {
            roleService.deleteRole(1L);
            verify(rolesRepository, times(1)).deleteById(1L);
        }
    }
}