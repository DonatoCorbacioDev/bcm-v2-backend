package com.donatodev.bcm_backend.mapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
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
 * This class verifies the mapping logic between {@link FinancialValues} entities
 * and {@link FinancialValueDTO} objects, including successful conversions and
 * proper exception handling when related entities are missing.
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
     * Tests that a {@link FinancialValues} entity is correctly converted to {@link FinancialValueDTO}.
     */
    @Test
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
     * Tests that a {@link FinancialValueDTO} is correctly converted to a {@link FinancialValues} entity.
     */
    @Test
    void shouldConvertToEntity() {
        FinancialTypes type = FinancialTypes.builder().id(1L).build();
        BusinessAreas area = BusinessAreas.builder().id(2L).build();
        Contracts contract = Contracts.builder().id(3L).build();

        FinancialValueDTO dto = new FinancialValueDTO(
                10L, 5, 2025, 1500.0,
                1L, 2L, 3L
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
     * Tests that a RuntimeException is thrown when the financial type is not found.
     */
    @Test
    void shouldThrowIfFinancialTypeNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(1L, 5, 2025, 1000.0, 99L, 2L, 3L);

        when(financialTypesRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            financialValueMapper.toEntity(dto);
        });

        assertEquals("Financial type not found", ex.getMessage());
    }

    /**
     * Tests that a RuntimeException is thrown when the business area is not found.
     */
    @Test
    void shouldThrowIfBusinessAreaNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(1L, 5, 2025, 1000.0, 1L, 99L, 3L);

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
    void shouldThrowIfContractNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(1L, 5, 2025, 1000.0, 1L, 2L, 99L);

        when(financialTypesRepository.findById(1L)).thenReturn(Optional.of(FinancialTypes.builder().id(1L).build()));
        when(businessAreaRepository.findById(2L)).thenReturn(Optional.of(BusinessAreas.builder().id(2L).build()));
        when(contractsRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            financialValueMapper.toEntity(dto);
        });

        assertEquals("Contract not found", ex.getMessage());
    }
}

