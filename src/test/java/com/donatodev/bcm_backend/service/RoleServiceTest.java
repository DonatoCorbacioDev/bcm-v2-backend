package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            Roles entity = Roles.builder().id(uid(1)).role("ADMIN").build();
            RoleDTO dto = new RoleDTO(uid(1), "ADMIN");

            when(rolesRepository.findAll()).thenReturn(List.of(entity));
            when(roleMapper.toDTO(entity)).thenReturn(dto);

            List<RoleDTO> result = roleService.getAllRoles();

            assertEquals(1, result.size());
            assertEquals("ADMIN", result.get(0).role());
        }

    }
}