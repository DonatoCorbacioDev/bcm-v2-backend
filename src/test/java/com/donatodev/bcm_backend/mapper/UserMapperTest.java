package com.donatodev.bcm_backend.mapper;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.donatodev.bcm_backend.util.TestDataCleaner;

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

    @Autowired
    private TestDataCleaner testDataCleaner;

    private Roles savedRole;
    private Managers savedManager;

    /**
     * Cleans up and initializes required entities before each test.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        testDataCleaner.clean();

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
                .id(uid(1))
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

    @Test
    void shouldShowAdminsWhetherTwoFactorIsOn() {
        Users user = Users.builder().id(uid(1)).username("totpUser").role(savedRole).totpEnabled(true).build();

        assertTrue(userMapper.toDTO(user).twoFactorEnabled());
    }

    @Test
    void shouldNeverTurnOnTwoFactorFromARequest() {
        UserDTO dto = new UserDTO(uid(1), "totpUser", "password123", null, savedRole.getId(),
                null, null, false, true);

        assertFalse(userMapper.toEntity(dto).isTotpEnabled());
    }

    /**
     * Tests conversion from {@link UserDTO} to {@link Users} entity with a
     * manager.
     */
    @Test
    void shouldMapToEntityWithManager() {
        UserDTO dto = new UserDTO(
                uid(1),
                "entityUser",
                "password123",
                savedManager.getId(),
                savedRole.getId(),
                null,
                null
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
                uid(2),
                "userNoManager",
                "securePass",
                null,
                savedRole.getId(),
                null,
                null
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
        UUID invalidManagerId = uid(999);
        UserDTO dto = new UserDTO(
                uid(3),
                "errorUser",
                "pass",
                invalidManagerId,
                savedRole.getId(),
                null,
                null
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
        UUID invalidRoleId = uid(888);
        UserDTO dto = new UserDTO(
                uid(4),
                "errorRoleUser",
                "pass",
                null,
                invalidRoleId,
                null,
                null
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
                .id(uid(5))
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
