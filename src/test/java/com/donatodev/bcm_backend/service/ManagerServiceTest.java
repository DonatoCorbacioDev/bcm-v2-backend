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

import com.donatodev.bcm_backend.dto.ManagerDTO;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.mapper.ManagerMapper;
import com.donatodev.bcm_backend.repository.ManagersRepository;

/**
 * Unit tests for {@link ManagerService}.
 * <p>
 * Verifies the behavior of the service layer responsible for manager-related operations.
 * This includes:
 * <ul>
 *     <li>Retrieving all managers</li>
 *     <li>Fetching a manager by ID</li>
 *     <li>Handling missing managers</li>
 *     <li>Creating a new manager</li>
 *     <li>Updating an existing manager</li>
 *     <li>Deleting a manager by ID</li>
 * </ul>
 * <p>
 * Tests are executed in order using {@link TestMethodOrder} with {@link Order}.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ManagerServiceTest {

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
    	 * Verifies that a list of ManagerDTOs is returned correctly from the repository.
    	 */
        @Test
        @Order(1)
        @DisplayName("Get all managers returns list of DTOs")
        void shouldGetAllManagers() {
            Managers entity = Managers.builder().id(1L).firstName("Mario").lastName("Rossi").build();
            ManagerDTO dto = new ManagerDTO(1L, "Mario", "Rossi", "mario@example.com", "1234567890", "IT");

            when(managersRepository.findAll()).thenReturn(List.of(entity));
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
            Managers entity = Managers.builder().id(1L).firstName("Luca").lastName("Bianchi").build();
            ManagerDTO dto = new ManagerDTO(1L, "Luca", "Bianchi", "luca@example.com", "0987654321", "HR");

            when(managersRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(managerMapper.toDTO(entity)).thenReturn(dto);

            ManagerDTO result = managerService.getManagerById(1L);

            assertEquals("Luca", result.firstName());
        }

        /**
         * Verifies that {@link com.donatodev.bcm_backend.exception.ManagerNotFoundException} 
         * is thrown when the manager ID does not exist.
         */
        @Test
        @Order(3)
        @DisplayName("Get manager by ID throws exception if not found")
        void shouldThrowIfManagerNotFound() {
            when(managersRepository.findById(999L)).thenReturn(Optional.empty());
            ManagerNotFoundException ex =
                assertThrows(ManagerNotFoundException.class, () -> managerService.getManagerById(999L));
            assertEquals("Manager ID 999 not found", ex.getMessage());
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
            Managers saved = Managers.builder().id(1L).firstName("Anna").build();
            ManagerDTO savedDTO = new ManagerDTO(1L, "Anna", "Verdi", "anna@example.com", "111222333", "Finance");

            when(managerMapper.toEntity(dto)).thenReturn(entity);
            when(managersRepository.save(entity)).thenReturn(saved);
            when(managerMapper.toDTO(saved)).thenReturn(savedDTO);

            ManagerDTO result = managerService.createManager(dto);

            assertEquals(1L, result.id());
            assertEquals("Anna", result.firstName());
        }

        /**
         * Verifies that an existing manager is updated correctly and the updated DTO is returned.
         */
        @Test
        @Order(5)
        @DisplayName("Update manager returns updated DTO")
        void shouldUpdateManager() {
            Managers existing = Managers.builder().id(1L).firstName("Paolo").build();
            ManagerDTO updateDTO = new ManagerDTO(1L, "Paolo", "Neri", "paolo@example.com", "444555666", "Marketing");
            ManagerDTO updatedDTO = new ManagerDTO(1L, "Paolo", "Neri", "paolo@example.com", "444555666", "Marketing");

            when(managersRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(managersRepository.save(existing)).thenReturn(existing);
            when(managerMapper.toDTO(existing)).thenReturn(updatedDTO);

            ManagerDTO result = managerService.updateManager(1L, updateDTO);

            assertEquals("Neri", result.lastName());
            assertEquals("Marketing", result.department());
        }


        /**
        * Verifies that {@link com.donatodev.bcm_backend.exception.ManagerNotFoundException} 
        * is thrown if the manager to update does not exist.
        */
        @Test
        @Order(6)
        @DisplayName("Update manager throws exception if not found")
        void shouldThrowWhenUpdatingMissingManager() {
            ManagerDTO dto = new ManagerDTO(1L, "Giulia", "Moretti", "giulia@example.com", "777888999", "Sales");
            when(managersRepository.findById(1L)).thenReturn(Optional.empty());

            ManagerNotFoundException ex =
                assertThrows(ManagerNotFoundException.class, () -> managerService.updateManager(1L, dto));
            assertEquals("Manager ID 1 not found", ex.getMessage());
        }

        /**
         * Verifies that the repository method is called to delete the manager by ID.
         */
        @Test
        @Order(7)
        @DisplayName("Delete manager calls repository")
        void shouldDeleteManager() {
            managerService.deleteManager(1L);
            verify(managersRepository, times(1)).deleteById(1L);
        }
    }
}