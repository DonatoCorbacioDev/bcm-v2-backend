package com.donatodev.bcm_backend.mapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.FinancialValueDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;

/**
 * Unit tests for {@link FinancialValueMapper}.
 * <p>
 * This class verifies the mapping logic between {@link FinancialValues}
 * entities and {@link FinancialValueDTO} objects, including successful
 * conversions and proper exception handling when related entities are missing.
 * </p>
 */
@ActiveProfiles("test")
class FinancialValueMapperTest {

    @Mock
    private FinancialTypesRepository financialTypesRepository;

    @Mock
    private BusinessAreasRepository businessAreaRepository;

    @Mock
    private ContractsRepository contractsRepository;

    @InjectMocks
    private FinancialValueMapper financialValueMapper;

    /**
     * Initializes mocks before each test.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * Tests that a {@link FinancialValues} entity is correctly converted to
     * {@link FinancialValueDTO}.
     */
    @Test
    @DisplayName("Convert entity to DTO")
    void shouldConvertToDTO() {
        FinancialTypes type = FinancialTypes.builder().id(1L).build();
        BusinessAreas area = BusinessAreas.builder().id(2L).build();
        Contracts contract = Contracts.builder().id(3L).build();

        FinancialValues value = FinancialValues.builder()
                .id(10L)
                .month(5)
                .year(2025)
                .financialAmount(1500.0)
                .financialType(type)
                .businessArea(area)
                .contract(contract)
                .build();

        FinancialValueDTO dto = financialValueMapper.toDTO(value);

        assertEquals(10L, dto.id());
        assertEquals(5, dto.month());
        assertEquals(2025, dto.year());
        assertEquals(1500.0, dto.financialAmount());
        assertEquals(1L, dto.financialTypeId());
        assertEquals(2L, dto.businessAreaId());
        assertEquals(3L, dto.contractId());
    }

    /**
     * Tests that a {@link FinancialValueDTO} is correctly converted to a
     * {@link FinancialValues} entity.
     */
    @Test
    @DisplayName("Convert DTO to entity")
    void shouldConvertToEntity() {
        FinancialTypes type = FinancialTypes.builder().id(1L).build();
        BusinessAreas area = BusinessAreas.builder().id(2L).build();
        Contracts contract = Contracts.builder().id(3L).build();

        FinancialValueDTO dto = new FinancialValueDTO(
                10L, 5, 2025, 1500.0,
                1L, 2L, 3L, "Type", "Area", "Contract"
        );

        when(financialTypesRepository.findById(1L)).thenReturn(Optional.of(type));
        when(businessAreaRepository.findById(2L)).thenReturn(Optional.of(area));
        when(contractsRepository.findById(3L)).thenReturn(Optional.of(contract));

        FinancialValues entity = financialValueMapper.toEntity(dto);

        assertEquals(10L, entity.getId());
        assertEquals(5, entity.getMonth());
        assertEquals(2025, entity.getYear());
        assertEquals(1500.0, entity.getFinancialAmount());
        assertEquals(type, entity.getFinancialType());
        assertEquals(area, entity.getBusinessArea());
        assertEquals(contract, entity.getContract());
    }

    /**
     * Tests that a RuntimeException is thrown when the financial type is not
     * found.
     */
    @Test
    @DisplayName("Throw exception if financial type not found")
    void shouldThrowIfFinancialTypeNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(1L, 5, 2025, 1000.0, 99L, 2L, 3L, "Type", "Area", "Contract");

        when(financialTypesRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            financialValueMapper.toEntity(dto);
        });

        assertEquals("Financial type not found", ex.getMessage());
    }

    /**
     * Tests that a RuntimeException is thrown when the business area is not
     * found.
     */
    @Test
    @DisplayName("Throw exception if business area not found")
    void shouldThrowIfBusinessAreaNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(1L, 5, 2025, 1000.0, 1L, 99L, 3L, "Type", "Area", "Contract");

        when(financialTypesRepository.findById(1L)).thenReturn(Optional.of(FinancialTypes.builder().id(1L).build()));
        when(businessAreaRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            financialValueMapper.toEntity(dto);
        });

        assertEquals("Business area not found", ex.getMessage());
    }

    /**
     * Tests that a RuntimeException is thrown when the contract is not found.
     */
    @Test
    @DisplayName("Throw exception if contract not found")
    void shouldThrowIfContractNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(1L, 5, 2025, 1000.0, 1L, 2L, 99L, "Type", "Area", "Contract");

        when(financialTypesRepository.findById(1L)).thenReturn(Optional.of(FinancialTypes.builder().id(1L).build()));
        when(businessAreaRepository.findById(2L)).thenReturn(Optional.of(BusinessAreas.builder().id(2L).build()));
        when(contractsRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            financialValueMapper.toEntity(dto);
        });

        assertEquals("Contract not found", ex.getMessage());
    }

    /**
     * Tests that toDTO returns null when entity is null.
     */
    @Test
    @DisplayName("toDTO should return null when entity is null")
    void shouldReturnNullWhenEntityIsNull() {
        FinancialValueDTO result = financialValueMapper.toDTO(null);
        assertNull(result);
    }

    /**
     * Tests that toDTO handles null financial type correctly.
     */
    @Test
    @DisplayName("toDTO should handle null financial type")
    void shouldHandleNullFinancialType() {
        FinancialValues entity = FinancialValues.builder()
                .id(1L)
                .month(1)
                .year(2025)
                .financialAmount(1000.00)
                .financialType(null)
                .businessArea(BusinessAreas.builder().id(1L).name("IT").build())
                .contract(Contracts.builder().id(1L).customerName("Client").build())
                .build();

        FinancialValueDTO result = financialValueMapper.toDTO(entity);

        assertNotNull(result);
        assertNull(result.financialTypeId());
        assertNull(result.typeName());
        assertEquals("IT", result.areaName());
        assertEquals("Client", result.customerName());
    }

    /**
     * Tests that toDTO handles null business area correctly.
     */
    @Test
    @DisplayName("toDTO should handle null business area")
    void shouldHandleNullBusinessArea() {
        FinancialValues entity = FinancialValues.builder()
                .id(1L)
                .month(1)
                .year(2025)
                .financialAmount(1000.00)
                .financialType(FinancialTypes.builder().id(1L).name("Revenue").build())
                .businessArea(null)
                .contract(Contracts.builder().id(1L).customerName("Client").build())
                .build();

        FinancialValueDTO result = financialValueMapper.toDTO(entity);

        assertNotNull(result);
        assertEquals("Revenue", result.typeName());
        assertNull(result.businessAreaId());
        assertNull(result.areaName());
        assertEquals("Client", result.customerName());
    }

    /**
     * Tests that toDTO handles null contract correctly.
     */
    @Test
    @DisplayName("toDTO should handle null contract")
    void shouldHandleNullContract() {
        FinancialValues entity = FinancialValues.builder()
                .id(1L)
                .month(1)
                .year(2025)
                .financialAmount(1000.00)
                .financialType(FinancialTypes.builder().id(1L).name("Revenue").build())
                .businessArea(BusinessAreas.builder().id(1L).name("IT").build())
                .contract(null)
                .build();

        FinancialValueDTO result = financialValueMapper.toDTO(entity);

        assertNotNull(result);
        assertEquals("Revenue", result.typeName());
        assertEquals("IT", result.areaName());
        assertNull(result.contractId());
        assertNull(result.customerName());
    }
}
