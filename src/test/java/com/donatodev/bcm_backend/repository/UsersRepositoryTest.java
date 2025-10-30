package com.donatodev.bcm_backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;


/**
 * Integration tests for {@link UsersRepository}.
 * <p>
 * These tests verify that {@link Users} entities can be correctly saved with associated
 * {@link Roles} and {@link Managers} using Spring Data JPA in a test profile.
 * </p>
 */
@DataJpaTest
@ActiveProfiles("test")
class UsersRepositoryTest {

    @Autowired 
    private UsersRepository usersRepository;

    @Autowired 
    private RolesRepository rolesRepository;

    @Autowired 
    private ManagersRepository managersRepository;

    /**
     * Cleans up all repositories before each test to ensure test isolation.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();
    }

    /**
     * Tests saving a {@link Users} entity with role MANAGER and associated {@link Managers} entity.
     */
    @Test
    @Order(1)
    @DisplayName("Should save user with manager role and associated manager")
    void shouldSaveManagerUserWithManager() {
        Roles role = rolesRepository.save(Roles.builder().role("MANAGER").build());
        Managers manager = managersRepository.save(Managers.builder()
                .firstName("Marco")
                .lastName("Rossi")
                .email("marco.rossi@example.com")
                .phoneNumber("123456")
                .department("Tech")
                .build());

        Users user = Users.builder()
                .username("manager1")
                .passwordHash("securePass")
                .verified(true)
                .role(role)
                .manager(manager)
                .build();

        Users saved = usersRepository.save(user);

        assertNotNull(saved.getId());
        assertEquals("manager1", saved.getUsername());
        assertEquals("MANAGER", saved.getRole().getRole());
        assertEquals(manager.getId(), saved.getManager().getId());
    }

    /**
     * Tests saving an ADMIN {@link Users} entity with an associated manager (even if not used logically).
     */
    @Test
    @Order(2)
    @DisplayName("Should save admin user with fake manager")
    void shouldSaveAdminUserWithManager() {
        Roles adminRole = rolesRepository.save(Roles.builder().role("ADMIN").build());

        Managers fakeManager = managersRepository.save(Managers.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@example.com")
                .phoneNumber("000000")
                .department("AdminDept")
                .build());

        Users admin = Users.builder()
                .username("admin")
                .passwordHash("admin123")
                .verified(true)
                .role(adminRole)
                .manager(fakeManager)
                .build();

        Users savedAdmin = usersRepository.save(admin);

        assertNotNull(savedAdmin.getId());
        assertEquals("admin", savedAdmin.getUsername());
        assertEquals("ADMIN", savedAdmin.getRole().getRole());
        assertEquals(fakeManager.getId(), savedAdmin.getManager().getId());
    }
}

