package com.donatodev.bcm_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.UserDTO;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.exception.RoleNotFoundException;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;

/**
 * Unit tests for {@link UserMapper}.
 * <p>
 * Verifies correct mapping between {@link Users} entities and {@link UserDTO}
 * objects, including handling of optional relationships (manager) and error
 * cases for missing entities.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private RolesRepository rolesRepository;

    private Roles savedRole;
    private Managers savedManager;

    /**
     * Cleans up and initializes required entities before each test.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        managersRepository.deleteAll();
        rolesRepository.deleteAll();

        savedRole = rolesRepository.save(Roles.builder().role("ADMIN").build());

        savedManager = managersRepository.save(Managers.builder()
                .firstName("Mario")
                .lastName("Rossi")
                .email("mario.rossi@mail.com")
                .build());
    }

    /**
     * Tests conversion from {@link Users} to {@link UserDTO}, ensuring all
     * fields are correctly mapped except for the password.
     */
    @Test
    void shouldMapToDTOCorrectly() {
        Users user = Users.builder()
                .id(1L)
                .username("dtoUser")
                .manager(savedManager)
                .role(savedRole)
                .build();

        UserDTO dto = userMapper.toDTO(user);

        assertEquals(user.getId(), dto.id());
        assertEquals(user.getUsername(), dto.username());
        assertEquals(savedManager.getId(), dto.managerId());
        assertEquals(savedRole.getId(), dto.roleId());
        assertNull(dto.password()); // password should not be returned in DTO
    }

    /**
     * Tests conversion from {@link UserDTO} to {@link Users} entity with a
     * manager.
     */
    @Test
    void shouldMapToEntityWithManager() {
        UserDTO dto = new UserDTO(
                1L,
                "entityUser",
                "password123",
                savedManager.getId(),
                savedRole.getId()
        );

        Users user = userMapper.toEntity(dto);

        assertEquals(dto.id(), user.getId());
        assertEquals(dto.username(), user.getUsername());
        assertEquals(dto.password(), user.getPasswordHash());
        assertEquals(savedManager.getId(), user.getManager().getId());
        assertEquals(savedRole.getId(), user.getRole().getId());
    }

    /**
     * Tests conversion from {@link UserDTO} to {@link Users} entity when
     * manager is null.
     */
    @Test
    void shouldMapToEntityWithoutManager() {
        UserDTO dto = new UserDTO(
                2L,
                "userNoManager",
                "securePass",
                null,
                savedRole.getId()
        );

        Users user = userMapper.toEntity(dto);

        assertEquals(dto.id(), user.getId());
        assertEquals(dto.username(), user.getUsername());
        assertNull(user.getManager());
        assertEquals(savedRole.getId(), user.getRole().getId());
    }

    /**
     * Tests that a {@link ManagerNotFoundException} is thrown when the manager
     * ID does not exist.
     */
    @Test
    void shouldThrowIfManagerNotFound() {
        Long invalidManagerId = 999L;
        UserDTO dto = new UserDTO(
                3L,
                "errorUser",
                "pass",
                invalidManagerId,
                savedRole.getId()
        );

        Exception ex = assertThrows(ManagerNotFoundException.class, () -> userMapper.toEntity(dto));
        assertEquals("Manager ID " + invalidManagerId + " not found", ex.getMessage());
    }

    /**
     * Tests that a {@link RoleNotFoundException} is thrown when the role ID
     * does not exist.
     */
    @Test
    void shouldThrowIfRoleNotFound() {
        Long invalidRoleId = 888L;
        UserDTO dto = new UserDTO(
                4L,
                "errorRoleUser",
                "pass",
                null,
                invalidRoleId
        );

        Exception ex = assertThrows(RoleNotFoundException.class, () -> userMapper.toEntity(dto));
        assertEquals("Role ID " + invalidRoleId + " not found", ex.getMessage());
    }

    /**
     * Tests conversion from {@link Users} to {@link UserDTO} when user has no
     * manager.
     */
    @Test
    void shouldMapToDTOWithoutManager() {
        Users user = Users.builder()
                .id(5L)
                .username("userWithoutManager")
                .manager(null) // No manager
                .role(savedRole)
                .build();

        UserDTO dto = userMapper.toDTO(user);

        assertEquals(user.getId(), dto.id());
        assertEquals(user.getUsername(), dto.username());
        assertNull(dto.managerId());  // Should be null when user has no manager
        assertEquals(savedRole.getId(), dto.roleId());
        assertNull(dto.password());
    }
}
