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

import com.donatodev.bcm_backend.dto.FinancialTypeDTO;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.exception.FinancialTypeNotFoundException;
import com.donatodev.bcm_backend.mapper.FinancialTypeMapper;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;

/**
 * Unit tests for {@link FinancialTypeService}.
 * <p>
 * This test class verifies the correct behavior of the service methods for managing
 * {@link com.donatodev.bcm_backend.entity.FinancialTypes}, including retrieval, creation,
 * updating and deletion. The repository and mapper are mocked.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class FinancialTypeServiceTest {

    @Mock
    private FinancialTypesRepository financialTypesRepository;

    @Mock
    private FinancialTypeMapper financialTypeMapper;

    @InjectMocks
    private FinancialTypeService financialTypeService;

    /**
     * Nested test class for grouped tests on FinancialTypeService.
     */
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: FinancialTypeService")
    @SuppressWarnings("unused")
    class VerifyFinancialTypeService {

    	/**
         * Should return all financial types as a list of DTOs.
         */
        @Test
        @Order(1)
        @DisplayName("Get all types returns list of DTOs")
        void shouldGetAllTypes() {
            FinancialTypes entity = FinancialTypes.builder().id(1L).name("Capex").description("Capital Expenses").build();
            FinancialTypeDTO dto = new FinancialTypeDTO(1L, "Capex", "Capital Expenses");

            when(financialTypesRepository.findAll()).thenReturn(List.of(entity));
            when(financialTypeMapper.toDTO(entity)).thenReturn(dto);

            List<FinancialTypeDTO> result = financialTypeService.getAllTypes();

            assertEquals(1, result.size());
            assertEquals("Capex", result.get(0).name());
        }

        /**
         * Should return the financial type DTO corresponding to the given ID.
         */
        @Test
        @Order(2)
        @DisplayName("Get type by ID returns DTO")
        void shouldGetTypeById() {
            FinancialTypes entity = FinancialTypes.builder().id(1L).name("Opex").description("Operating Expenses").build();
            FinancialTypeDTO dto = new FinancialTypeDTO(1L, "Opex", "Operating Expenses");

            when(financialTypesRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(financialTypeMapper.toDTO(entity)).thenReturn(dto);

            FinancialTypeDTO result = financialTypeService.getTypeById(1L);

            assertEquals("Opex", result.name());
        }

        /**
         * Should throw FinancialTypeNotFoundException when ID is not found.
         */
        @Test
        @Order(3)
        @DisplayName("Get type by ID throws if not found")
        void shouldThrowWhenTypeNotFound() {
            when(financialTypesRepository.findById(999L)).thenReturn(Optional.empty());

            FinancialTypeNotFoundException ex =
                assertThrows(FinancialTypeNotFoundException.class, () -> financialTypeService.getTypeById(999L));
            assertEquals("Financial type ID 999 not found", ex.getMessage());
        }

        /**
         * Should create a new financial type and return the saved DTO.
         */
        @Test
        @Order(4)
        @DisplayName("Create type returns saved DTO")
        void shouldCreateType() {
            FinancialTypeDTO dto = new FinancialTypeDTO(null, "Investiments", "Description");
            FinancialTypes entity = FinancialTypes.builder().name("Investiments").description("Description").build();
            FinancialTypes saved = FinancialTypes.builder().id(1L).name("Investiments").description("Description").build();
            FinancialTypeDTO savedDTO = new FinancialTypeDTO(1L, "Investiments", "Description");

            when(financialTypeMapper.toEntity(dto)).thenReturn(entity);
            when(financialTypesRepository.save(entity)).thenReturn(saved);
            when(financialTypeMapper.toDTO(saved)).thenReturn(savedDTO);

            FinancialTypeDTO result = financialTypeService.createType(dto);

            assertEquals(1L, result.id());
            assertEquals("Investiments", result.name());
        }

        /**
         * Should update an existing financial type and return the updated DTO.
         */
        @Test
        @Order(5)
        @DisplayName("Update type returns updated DTO")
        void shouldUpdateType() {
            FinancialTypes existing = FinancialTypes.builder().id(1L).name("Old").description("Old desc").build();
            FinancialTypeDTO updateDTO = new FinancialTypeDTO(1L, "Updated", "Updated desc");
            FinancialTypes updatedEntity = FinancialTypes.builder().id(1L).name("Updated").description("Updated desc").build();

            when(financialTypesRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(financialTypesRepository.save(existing)).thenReturn(updatedEntity);
            when(financialTypeMapper.toDTO(updatedEntity)).thenReturn(updateDTO);

            FinancialTypeDTO result = financialTypeService.updateType(1L, updateDTO);

            assertEquals("Updated", result.name());
            assertEquals("Updated desc", result.description());
        }

        /**
         * Should delete the financial type with the given ID.
         */
        @Test
        @Order(6)
        @DisplayName("Delete type calls repository")
        void shouldDeleteType() {
            financialTypeService.deleteType(1L);
            verify(financialTypesRepository, times(1)).deleteById(1L);
        }
        
        /**
         * Should throw FinancialTypeNotFoundException if trying to update a non-existent type.
         */
        @Test
        @Order(7)
        @DisplayName("Update type throws if not found")
        void shouldThrowWhenUpdatingMissingType() {
            FinancialTypeDTO updateDTO = new FinancialTypeDTO(999L, "Missing", "Does not exist");

            when(financialTypesRepository.findById(999L)).thenReturn(Optional.empty());

            FinancialTypeNotFoundException ex =
                assertThrows(FinancialTypeNotFoundException.class,
                    () -> financialTypeService.updateType(999L, updateDTO));
            assertEquals("Financial type ID 999 not found", ex.getMessage());
        }
    }
}