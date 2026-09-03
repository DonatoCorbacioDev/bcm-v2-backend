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
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.exception.BusinessAreaNotFoundException;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.FinancialTypeNotFoundException;
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
        FinancialTypes type = FinancialTypes.builder().id(1L).category(FinancialCategory.REVENUE).build();
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
        assertEquals(FinancialCategory.REVENUE, dto.category());
    }

    /**
     * Tests that a {@link FinancialValueDTO} is correctly converted to a
     * {@link FinancialValues} entity.
     */
    @Test
    @DisplayName("Convert DTO to entity, scoped to the caller's org")
    void shouldConvertToEntity() {
        FinancialTypes type = FinancialTypes.builder().id(1L).build();
        BusinessAreas area = BusinessAreas.builder().id(2L).build();
        Contracts contract = Contracts.builder().id(3L).build();

        FinancialValueDTO dto = new FinancialValueDTO(
                10L, 5, 2025, 1500.0,
                1L, 2L, 3L, "Type", "Area", "Contract"
        , FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(1L, 7L)).thenReturn(Optional.of(type));
        when(businessAreaRepository.findByIdAndOrganizationId(2L, 7L)).thenReturn(Optional.of(area));
        when(contractsRepository.findByIdAndOrganization_Id(3L, 7L)).thenReturn(Optional.of(contract));

        FinancialValues entity = financialValueMapper.toEntity(dto, 7L);

        assertEquals(10L, entity.getId());
        assertEquals(5, entity.getMonth());
        assertEquals(2025, entity.getYear());
        assertEquals(1500.0, entity.getFinancialAmount());
        assertEquals(type, entity.getFinancialType());
        assertEquals(area, entity.getBusinessArea());
        assertEquals(contract, entity.getContract());
    }

    @Test
    @DisplayName("Convert DTO to entity falls back to an unscoped lookup when orgId is null")
    void shouldConvertToEntityWithoutOrgScope() {
        FinancialTypes type = FinancialTypes.builder().id(1L).build();
        BusinessAreas area = BusinessAreas.builder().id(2L).build();
        Contracts contract = Contracts.builder().id(3L).build();

        FinancialValueDTO dto = new FinancialValueDTO(
                10L, 5, 2025, 1500.0, 1L, 2L, 3L, "Type", "Area", "Contract", FinancialCategory.REVENUE);

        when(financialTypesRepository.findById(1L)).thenReturn(Optional.of(type));
        when(businessAreaRepository.findById(2L)).thenReturn(Optional.of(area));
        when(contractsRepository.findById(3L)).thenReturn(Optional.of(contract));

        FinancialValues entity = financialValueMapper.toEntity(dto, null);

        assertEquals(type, entity.getFinancialType());
        assertEquals(area, entity.getBusinessArea());
        assertEquals(contract, entity.getContract());
    }

    /**
     * A financialTypeId/businessAreaId/contractId that exists in the DB but
     * belongs to a different organization must be rejected exactly like a
     * nonexistent ID — this is the actual security boundary, not just a
     * not-found check. Regression test for the cross-tenant financial-value
     * association fix.
     */
    @Test
    @DisplayName("Throw exception if financial type belongs to another org (or does not exist)")
    void shouldThrowIfFinancialTypeNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(1L, 5, 2025, 1000.0, 99L, 2L, 3L, "Type", "Area", "Contract", FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(99L, 7L)).thenReturn(Optional.empty());

        FinancialTypeNotFoundException ex = assertThrows(FinancialTypeNotFoundException.class, () -> {
            financialValueMapper.toEntity(dto, 7L);
        });

        assertEquals("Tipo finanziario ID 99 non trovato", ex.getMessage());
    }

    @Test
    @DisplayName("Throw exception if business area belongs to another org (or does not exist)")
    void shouldThrowIfBusinessAreaNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(1L, 5, 2025, 1000.0, 1L, 99L, 3L, "Type", "Area", "Contract", FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(1L, 7L))
                .thenReturn(Optional.of(FinancialTypes.builder().id(1L).build()));
        when(businessAreaRepository.findByIdAndOrganizationId(99L, 7L)).thenReturn(Optional.empty());

        BusinessAreaNotFoundException ex = assertThrows(BusinessAreaNotFoundException.class, () -> {
            financialValueMapper.toEntity(dto, 7L);
        });

        assertEquals("Area di business ID 99 non trovata", ex.getMessage());
    }

    @Test
    @DisplayName("Throw exception if contract belongs to another org (or does not exist)")
    void shouldThrowIfContractNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(1L, 5, 2025, 1000.0, 1L, 2L, 99L, "Type", "Area", "Contract", FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(1L, 7L))
                .thenReturn(Optional.of(FinancialTypes.builder().id(1L).build()));
        when(businessAreaRepository.findByIdAndOrganizationId(2L, 7L))
                .thenReturn(Optional.of(BusinessAreas.builder().id(2L).build()));
        when(contractsRepository.findByIdAndOrganization_Id(99L, 7L)).thenReturn(Optional.empty());

        ContractNotFoundException ex = assertThrows(ContractNotFoundException.class, () -> {
            financialValueMapper.toEntity(dto, 7L);
        });

        assertEquals("Contratto ID 99 non trovato", ex.getMessage());
    }

    /**
     * Tests that updateEntity updates all scalar and relation fields in-place.
     */
    @Test
    @DisplayName("updateEntity should update all fields including relations")
    void shouldUpdateEntityInPlace() {
        FinancialTypes newType = FinancialTypes.builder().id(2L).build();
        BusinessAreas newArea = BusinessAreas.builder().id(3L).build();
        Contracts newContract = Contracts.builder().id(4L).build();

        FinancialValues existing = FinancialValues.builder()
                .id(10L)
                .month(1)
                .year(2024)
                .financialAmount(100.0)
                .financialType(FinancialTypes.builder().id(1L).build())
                .businessArea(BusinessAreas.builder().id(1L).build())
                .contract(Contracts.builder().id(1L).build())
                .build();

        FinancialValueDTO dto = new FinancialValueDTO(10L, 6, 2025, 999.0, 2L, 3L, 4L, "NewType", "NewArea", "NewContract", FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(2L, 7L)).thenReturn(Optional.of(newType));
        when(businessAreaRepository.findByIdAndOrganizationId(3L, 7L)).thenReturn(Optional.of(newArea));
        when(contractsRepository.findByIdAndOrganization_Id(4L, 7L)).thenReturn(Optional.of(newContract));

        financialValueMapper.updateEntity(existing, dto, 7L);

        assertEquals(6, existing.getMonth());
        assertEquals(2025, existing.getYear());
        assertEquals(999.0, existing.getFinancialAmount());
        assertEquals(newType, existing.getFinancialType());
        assertEquals(newArea, existing.getBusinessArea());
        assertEquals(newContract, existing.getContract());
    }

    /**
     * A financial type belonging to another org (or nonexistent) must not be
     * attachable via updateEntity either — the same cross-tenant boundary as
     * toEntity applies on update.
     */
    @Test
    @DisplayName("updateEntity should throw if financial type belongs to another org (or does not exist)")
    void shouldThrowOnUpdateIfFinancialTypeNotFound() {
        FinancialValues existing = FinancialValues.builder().id(1L).build();
        FinancialValueDTO dto = new FinancialValueDTO(1L, 1, 2025, 100.0, 99L, 2L, 3L, null, null, null, FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(99L, 7L)).thenReturn(Optional.empty());

        FinancialTypeNotFoundException ex = assertThrows(FinancialTypeNotFoundException.class,
                () -> financialValueMapper.updateEntity(existing, dto, 7L));
        assertEquals("Tipo finanziario ID 99 non trovato", ex.getMessage());
    }

    @Test
    @DisplayName("updateEntity should throw if business area belongs to another org (or does not exist)")
    void shouldThrowOnUpdateIfBusinessAreaNotFound() {
        FinancialValues existing = FinancialValues.builder().id(1L).build();
        FinancialValueDTO dto = new FinancialValueDTO(1L, 1, 2025, 100.0, 1L, 99L, 3L, null, null, null, FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(1L, 7L))
                .thenReturn(Optional.of(FinancialTypes.builder().id(1L).build()));
        when(businessAreaRepository.findByIdAndOrganizationId(99L, 7L)).thenReturn(Optional.empty());

        BusinessAreaNotFoundException ex = assertThrows(BusinessAreaNotFoundException.class,
                () -> financialValueMapper.updateEntity(existing, dto, 7L));
        assertEquals("Area di business ID 99 non trovata", ex.getMessage());
    }

    @Test
    @DisplayName("updateEntity should throw if contract belongs to another org (or does not exist)")
    void shouldThrowOnUpdateIfContractNotFound() {
        FinancialValues existing = FinancialValues.builder().id(1L).build();
        FinancialValueDTO dto = new FinancialValueDTO(1L, 1, 2025, 100.0, 1L, 2L, 99L, null, null, null, FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(1L, 7L))
                .thenReturn(Optional.of(FinancialTypes.builder().id(1L).build()));
        when(businessAreaRepository.findByIdAndOrganizationId(2L, 7L))
                .thenReturn(Optional.of(BusinessAreas.builder().id(2L).build()));
        when(contractsRepository.findByIdAndOrganization_Id(99L, 7L)).thenReturn(Optional.empty());

        ContractNotFoundException ex = assertThrows(ContractNotFoundException.class,
                () -> financialValueMapper.updateEntity(existing, dto, 7L));
        assertEquals("Contratto ID 99 non trovato", ex.getMessage());
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
        assertNull(result.category());
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
