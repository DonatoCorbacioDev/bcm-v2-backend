package com.donatodev.bcm_backend.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.BusinessAreaDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.exception.BusinessAreaNotFoundException;
import com.donatodev.bcm_backend.mapper.BusinessAreaMapper;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;

/**
 * Unit tests for the {@link com.donatodev.bcm_backend.service.BusinessAreaService} class.
 * <p>
 * This test class verifies the correctness of the business logic related to business area management,
 * using mocked dependencies for repository and mapper to isolate the service layer.
 * </p>
 * 
 * Main functionalities tested:
 * <ul>
 *     <li>Retrieving all business areas</li>
 *     <li>Retrieving a single area by ID</li>
 *     <li>Creating a new business area</li>
 *     <li>Updating an existing business area</li>
 *     <li>Deleting a business area</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class BusinessAreaServiceTest {

    @Mock
    private BusinessAreasRepository repository;

    @Mock
    private BusinessAreaMapper mapper;

    @InjectMocks
    private BusinessAreaService service;

    /**
     * Nested test suite for all methods in BusinessAreaService.
     * <p>Ordered to ensure clarity during test execution.</p>
     */
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: BusinessAreaService")
    @SuppressWarnings("unused")
    class VerifyBusinessAreaService {

    	/**
         * Test: getAllAreas() should return a list of DTOs when areas exist.
         */
        @Test
        @Order(1)
        @DisplayName("Get all areas returns list of DTOs")
        void shouldGetAllAreas() {
            BusinessAreas entity1 = BusinessAreas.builder().id(1L).name("Finance").description("Budgeting").build();
            BusinessAreas entity2 = BusinessAreas.builder().id(2L).name("HR").description("Hiring").build();
            BusinessAreaDTO dto1 = new BusinessAreaDTO(1L, "Finance", "Budgeting");
            BusinessAreaDTO dto2 = new BusinessAreaDTO(2L, "HR", "Hiring");

            when(repository.findAll()).thenReturn(Arrays.asList(entity1, entity2));
            when(mapper.toDTO(entity1)).thenReturn(dto1);
            when(mapper.toDTO(entity2)).thenReturn(dto2);

            List<BusinessAreaDTO> result = service.getAllAreas();

            assertEquals(2, result.size());
            assertEquals("Finance", result.get(0).name());
            assertEquals("HR", result.get(1).name());
        }

        /**
         * Test: getAreaById() should return a DTO if the area exists.
         */
        @Test
        @Order(2)
        @DisplayName("Get area by ID returns DTO")
        void shouldGetAreaById() {
            BusinessAreas entity = BusinessAreas.builder().id(1L).name("IT").description("Infra").build();
            BusinessAreaDTO dto = new BusinessAreaDTO(1L, "IT", "Infra");

            when(repository.findById(1L)).thenReturn(Optional.of(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            BusinessAreaDTO result = service.getAreaById(1L);

            assertEquals("IT", result.name());
            assertEquals("Infra", result.description());
        }

        /**
         * Test: getAreaById() should throw BusinessAreaNotFoundException if not found.
         */
        @Test
        @Order(3)
        @DisplayName("Get area by ID throws exception if not found")
        void shouldThrowExceptionWhenAreaNotFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            BusinessAreaNotFoundException ex =
                assertThrows(BusinessAreaNotFoundException.class, () -> service.getAreaById(999L));
            assertEquals("Business area ID 999 not found", ex.getMessage());
        }

        /**
         * Test: createArea() should save a new area and return the saved DTO.
         */
        @Test
        @Order(4)
        @DisplayName("Create area returns saved DTO")
        void shouldCreateArea() {
            BusinessAreaDTO dto = new BusinessAreaDTO(null, "New", "New Desc");
            BusinessAreas entity = BusinessAreas.builder().name("New").description("New Desc").build();
            BusinessAreas savedEntity = BusinessAreas.builder().id(1L).name("New").description("New Desc").build();
            BusinessAreaDTO savedDTO = new BusinessAreaDTO(1L, "New", "New Desc");

            when(mapper.toEntity(dto)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(savedEntity);
            when(mapper.toDTO(savedEntity)).thenReturn(savedDTO);

            BusinessAreaDTO result = service.createArea(dto);

            assertEquals(1L, result.id());
            assertEquals("New", result.name());
        }

        /**
         * Test: updateArea() should update the area and return the updated DTO.
         */
        @Test
        @Order(5)
        @DisplayName("Update area returns updated DTO")
        void shouldUpdateArea() {
            BusinessAreas existing = BusinessAreas.builder().id(1L).name("Old").description("Old Desc").build();
            BusinessAreaDTO updatedDTO = new BusinessAreaDTO(1L, "Updated", "Updated Desc");

            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any())).thenReturn(updatedDTO);

            BusinessAreaDTO result = service.updateArea(1L, updatedDTO);

            assertEquals("Updated", result.name());
            assertEquals("Updated Desc", result.description());
        }

        /**
         * Test: updateArea() should throw BusinessAreaNotFoundException if area is missing.
         */
        @Test
        @Order(6)
        @DisplayName("Update area throws exception if not found")
        void shouldThrowExceptionWhenUpdatingMissingArea() {
            BusinessAreaDTO updatedDTO = new BusinessAreaDTO(1L, "Updated", "Updated Desc");
            when(repository.findById(1L)).thenReturn(Optional.empty());

            BusinessAreaNotFoundException ex =
                assertThrows(BusinessAreaNotFoundException.class, () -> service.updateArea(1L, updatedDTO));
            assertEquals("Business area ID 1 not found", ex.getMessage());
        }


        /**
         * Test: deleteArea() should call deleteById on the repository.
         */
        @Test
        @Order(7)
        @DisplayName("Delete area calls repository")
        void shouldDeleteArea() {
            Long id = 1L;
            service.deleteArea(id);
            verify(repository, times(1)).deleteById(id);
        }
        
        /**
         * Test: deleteArea() should not throw if the area does not exist.
         */
        @Test
        @Order(8)
        @DisplayName("Delete area throws no exception if area doesn't exist")
        void shouldNotThrowWhenDeletingNonExistentArea() {
            // Nessun comportamento specifico, perché deleteById non lancia eccezioni se l'ID non esiste
            assertDoesNotThrow(() -> service.deleteArea(999L));
        }
    }
}