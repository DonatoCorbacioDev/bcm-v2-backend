package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.ManagerDTO;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.mapper.ManagerMapper;
import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.repository.ManagersRepository;

/**
 * Unit tests for {@link ManagerService}.
 * <p>
 * Verifies the behavior of the service layer responsible for manager-related
 * operations. This includes:
 * <ul>
 * <li>Retrieving all managers</li>
 * <li>Fetching a manager by ID</li>
 * <li>Handling missing managers</li>
 * <li>Creating a new manager</li>
 * <li>Updating an existing manager</li>
 * <li>Deleting a manager by ID</li>
 * </ul>
 * <p>
 * Tests are executed in order using {@link TestMethodOrder} with {@link Order}.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ManagerServiceTest {

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

    @BeforeEach
    void enterTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void leaveTenant() {
        TenantContext.clear();
    }

    @Mock
    private ManagersRepository managersRepository;

    @Mock
    private ManagerMapper managerMapper;

    @InjectMocks
    private ManagerService managerService;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: ManagerService")
    @SuppressWarnings("unused")
    class VerifyManagerService {

        /**
         * Verifies that a list of ManagerDTOs is returned correctly from the
         * repository.
         */
        @Test
        @Order(1)
        @DisplayName("Get all managers returns list of DTOs")
        void shouldGetAllManagers() {
            Managers entity = Managers.builder().id(uid(1)).firstName("Mario").lastName("Rossi").build();
            ManagerDTO dto = new ManagerDTO(uid(1), "Mario", "Rossi", "mario@example.com", "1234567890", "IT");

            when(managersRepository.findByOrganizationId(TENANT, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(entity)));
            when(managerMapper.toDTO(entity)).thenReturn(dto);

            List<ManagerDTO> result = managerService.getAllManagers();

            assertEquals(1, result.size());
            assertEquals("Mario", result.get(0).firstName());
        }

        /**
         * Verifies that the correct ManagerDTO is returned for the given ID.
         */
        @Test
        @Order(2)
        @DisplayName("Get manager by ID returns DTO")
        void shouldGetManagerById() {
            Managers entity = Managers.builder().id(uid(1)).firstName("Luca").lastName("Bianchi").build();
            ManagerDTO dto = new ManagerDTO(uid(1), "Luca", "Bianchi", "luca@example.com", "0987654321", "HR");

            when(managersRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(managerMapper.toDTO(entity)).thenReturn(dto);

            ManagerDTO result = managerService.getManagerById(uid(1));

            assertEquals("Luca", result.firstName());
        }

        /**
         * Verifies that
         * {@link com.donatodev.bcm_backend.exception.ManagerNotFoundException}
         * is thrown when the manager ID does not exist.
         */
        @Test
        @Order(3)
        @DisplayName("Get manager by ID throws exception if not found")
        void shouldThrowIfManagerNotFound() {
            when(managersRepository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());
            UUID id999 = uid(999);
            ManagerNotFoundException ex
                    = assertThrows(ManagerNotFoundException.class, () -> managerService.getManagerById(id999));
            assertEquals("ID manager " + uid(999) + " non trovato", ex.getMessage());
        }

        /**
         * Verifies that a new manager is created and returned as a DTO.
         */
        @Test
        @Order(4)
        @DisplayName("Create manager returns saved DTO")
        void shouldCreateManager() {
            ManagerDTO dto = new ManagerDTO(null, "Anna", "Verdi", "anna@example.com", "111222333", "Finance");
            Managers entity = Managers.builder().firstName("Anna").build();
            Managers saved = Managers.builder().id(uid(1)).firstName("Anna").build();
            ManagerDTO savedDTO = new ManagerDTO(uid(1), "Anna", "Verdi", "anna@example.com", "111222333", "Finance");

            when(managerMapper.toEntity(dto)).thenReturn(entity);
            when(managersRepository.save(entity)).thenReturn(saved);
            when(managerMapper.toDTO(saved)).thenReturn(savedDTO);

            ManagerDTO result = managerService.createManager(dto);

            assertEquals(uid(1), result.id());
            assertEquals("Anna", result.firstName());
        }

        /**
         * Verifies that an existing manager is updated correctly and the
         * updated DTO is returned.
         */
        @Test
        @Order(5)
        @DisplayName("Update manager returns updated DTO")
        void shouldUpdateManager() {
            Managers existing = Managers.builder().id(uid(1)).firstName("Paolo").build();
            ManagerDTO updateDTO = new ManagerDTO(uid(1), "Paolo", "Neri", "paolo@example.com", "444555666", "Marketing");
            ManagerDTO updatedDTO = new ManagerDTO(uid(1), "Paolo", "Neri", "paolo@example.com", "444555666", "Marketing");

            when(managersRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(existing));
            when(managersRepository.save(existing)).thenReturn(existing);
            when(managerMapper.toDTO(existing)).thenReturn(updatedDTO);

            ManagerDTO result = managerService.updateManager(uid(1), updateDTO);

            assertEquals("Neri", result.lastName());
            assertEquals("Marketing", result.department());
        }

        /**
         * Verifies that
         * {@link com.donatodev.bcm_backend.exception.ManagerNotFoundException}
         * is thrown if the manager to update does not exist.
         */
        @Test
        @Order(6)
        @DisplayName("Update manager throws exception if not found")
        void shouldThrowWhenUpdatingMissingManager() {
            ManagerDTO dto = new ManagerDTO(uid(1), "Giulia", "Moretti", "giulia@example.com", "777888999", "Sales");
            when(managersRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.empty());

            UUID id1 = uid(1);
            ManagerNotFoundException ex
                    = assertThrows(ManagerNotFoundException.class, () -> managerService.updateManager(id1, dto));
            assertEquals("ID manager " + uid(1) + " non trovato", ex.getMessage());
        }

        /**
         * Verifies that the repository method is called to delete the manager
         * by ID.
         */
        @Test
        @Order(7)
        @DisplayName("Delete manager calls repository")
        void shouldDeleteManager() {
            Managers manager = Managers.builder().id(uid(1)).build();
            when(managersRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(manager));

            managerService.deleteManager(uid(1));

            verify(managersRepository, times(1)).delete(manager);
        }

        @Test
        @Order(70)
        @DisplayName("Delete manager throws if not found")
        void shouldThrowWhenDeletingMissingManager() {
            when(managersRepository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            UUID id999 = uid(999);
            ManagerNotFoundException ex = assertThrows(ManagerNotFoundException.class,
                    () -> managerService.deleteManager(id999));

            assertEquals("ID manager " + uid(999) + " non trovato", ex.getMessage());
            verify(managersRepository, never()).delete(any(Managers.class));
        }

        @Test
        @Order(11)
        @DisplayName("Get manager entity returns entity when found")
        void shouldGetManagerEntity() {
            Managers entity = Managers.builder().id(uid(1)).firstName("Mario").lastName("Rossi").build();

            when(managersRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));

            Managers result = managerService.getManagerEntity(uid(1));

            assertEquals(uid(1), result.getId());
            assertEquals("Mario", result.getFirstName());
        }

        @Test
        @Order(12)
        @DisplayName("Get manager entity throws exception when not found")
        void shouldThrowWhenGetManagerEntityNotFound() {
            when(managersRepository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            UUID id999 = uid(999);
            ManagerNotFoundException ex = assertThrows(
                    ManagerNotFoundException.class,
                    () -> managerService.getManagerEntity(id999)
            );

            assertEquals("ID manager " + uid(999) + " non trovato", ex.getMessage());
        }

        @Test
        @Order(13)
        @DisplayName("getAllManagers with TenantContext uses org-filtered repository")
        void shouldGetAllManagersWithTenantContext() {
            Managers entity = Managers.builder().id(uid(1)).firstName("Mario").build();
            ManagerDTO dto = new ManagerDTO(uid(1), "Mario", "Rossi", "mario@example.com", "123", "IT");
            Page<Managers> page = new PageImpl<>(List.of(entity));

            TenantContext.set(uid(3));
            try {
                when(managersRepository.findByOrganizationId(eq(uid(3)), any(Pageable.class))).thenReturn(page);
                when(managerMapper.toDTO(entity)).thenReturn(dto);

                List<ManagerDTO> result = managerService.getAllManagers();

                assertEquals(1, result.size());
                verify(managersRepository).findByOrganizationId(eq(uid(3)), any(Pageable.class));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(14)
        @DisplayName("createManager with TenantContext sets organization on entity")
        void shouldCreateManagerWithTenantContext() {
            ManagerDTO dto = new ManagerDTO(null, "Anna", "Verdi", "anna@example.com", "111", "Finance");
            Managers entity = Managers.builder().firstName("Anna").build();
            Managers saved = Managers.builder().id(uid(2)).firstName("Anna").build();
            ManagerDTO savedDTO = new ManagerDTO(uid(2), "Anna", "Verdi", "anna@example.com", "111", "Finance");

            TenantContext.set(uid(4));
            try {
                when(managerMapper.toEntity(dto)).thenReturn(entity);
                when(managersRepository.save(entity)).thenReturn(saved);
                when(managerMapper.toDTO(saved)).thenReturn(savedDTO);

                ManagerDTO result = managerService.createManager(dto);

                assertEquals(uid(2), result.id());
                assertNotNull(entity.getOrganization());
                assertEquals(uid(4), entity.getOrganization().getId());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(17)
        @DisplayName("getManagerById with TenantContext uses org-scoped repository")
        void shouldGetManagerByIdWithTenantContext() {
            Managers entity = Managers.builder().id(uid(1)).firstName("Luca").lastName("Bianchi").build();
            ManagerDTO dto = new ManagerDTO(uid(1), "Luca", "Bianchi", "luca@example.com", "0987654321", "HR");

            TenantContext.set(uid(9));
            try {
                when(managersRepository.findByIdAndOrganizationId(uid(1), uid(9))).thenReturn(Optional.of(entity));
                when(managerMapper.toDTO(entity)).thenReturn(dto);

                ManagerDTO result = managerService.getManagerById(uid(1));

                assertEquals("Luca", result.firstName());
                verify(managersRepository).findByIdAndOrganizationId(uid(1), uid(9));
            } finally {
                TenantContext.clear();
            }
        }
    }
}
