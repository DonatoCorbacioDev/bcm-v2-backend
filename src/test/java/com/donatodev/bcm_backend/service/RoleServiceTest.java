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
            assertEquals("Ruolo ID 999 non trovato", ex.getMessage());
        }
    }
}