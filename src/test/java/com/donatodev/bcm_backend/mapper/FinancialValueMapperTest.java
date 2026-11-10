package com.donatodev.bcm_backend.mapper;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import org.junit.jupiter.api.AfterEach;
import com.donatodev.bcm_backend.config.TenantContext;
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
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
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

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

    /**
     * Enters the test tenant and initializes the mocks before each test.
     */
    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        TenantContext.set(TENANT);
        MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void leaveTenant() {
        TenantContext.clear();
    }

    @Mock
    private FinancialTypesRepository financialTypesRepository;

    @Mock
    private BusinessAreasRepository businessAreaRepository;

    @Mock
    private ContractsRepository contractsRepository;

    @InjectMocks
    private FinancialValueMapper financialValueMapper;

    /**
     * Tests that a {@link FinancialValues} entity is correctly converted to
     * {@link FinancialValueDTO}.
     */
    @Test
    @DisplayName("Convert entity to DTO")
    void shouldConvertToDTO() {
        FinancialTypes type = FinancialTypes.builder().id(uid(1)).category(FinancialCategory.REVENUE).build();
        BusinessAreas area = BusinessAreas.builder().id(uid(2)).build();
        Contracts contract = Contracts.builder().id(uid(3))
                .counterparty(Counterparty.builder().id(uid(4)).name("Client").type(CounterpartyType.CUSTOMER).build())
                .build();

        FinancialValues value = FinancialValues.builder()
                .id(uid(10))
                .month(5)
                .year(2025)
                .financialAmount(1500.0)
                .financialType(type)
                .businessArea(area)
                .contract(contract)
                .build();

        FinancialValueDTO dto = financialValueMapper.toDTO(value);

        assertEquals(uid(10), dto.id());
        assertEquals(5, dto.month());
        assertEquals(2025, dto.year());
        assertEquals(1500.0, dto.financialAmount());
        assertEquals(uid(1), dto.financialTypeId());
        assertEquals(uid(2), dto.businessAreaId());
        assertEquals(uid(3), dto.contractId());
        assertEquals(FinancialCategory.REVENUE, dto.category());
    }

    /**
     * Tests that a {@link FinancialValueDTO} is correctly converted to a
     * {@link FinancialValues} entity.
     */
    @Test
    @DisplayName("Convert DTO to entity, scoped to the caller's org")
    void shouldConvertToEntity() {
        FinancialTypes type = FinancialTypes.builder().id(uid(1)).build();
        BusinessAreas area = BusinessAreas.builder().id(uid(2)).build();
        Contracts contract = Contracts.builder().id(uid(3)).build();

        FinancialValueDTO dto = new FinancialValueDTO(
                uid(10), 5, 2025, 1500.0,
                uid(1), uid(2), uid(3), "Type", "Area", "Contract"
        , FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(uid(1), uid(7))).thenReturn(Optional.of(type));
        when(businessAreaRepository.findByIdAndOrganizationId(uid(2), uid(7))).thenReturn(Optional.of(area));
        when(contractsRepository.findByIdAndOrganization_Id(uid(3), uid(7))).thenReturn(Optional.of(contract));

        FinancialValues entity = financialValueMapper.toEntity(dto, uid(7));

        assertEquals(uid(10), entity.getId());
        assertEquals(5, entity.getMonth());
        assertEquals(2025, entity.getYear());
        assertEquals(1500.0, entity.getFinancialAmount());
        assertEquals(type, entity.getFinancialType());
        assertEquals(area, entity.getBusinessArea());
        assertEquals(contract, entity.getContract());
    }

    @Test
    @DisplayName("Without an organization nothing is resolved: no lookup across organizations")
    void shouldConvertToEntityWithoutOrgScope() {
        FinancialValueDTO dto = new FinancialValueDTO(
                uid(10), 5, 2025, 1500.0, uid(1), uid(2), uid(3), "Type", "Area", "Contract", FinancialCategory.REVENUE);

        assertThrows(FinancialTypeNotFoundException.class, () -> financialValueMapper.toEntity(dto, null));
        verify(financialTypesRepository, never()).findById(any());
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
        FinancialValueDTO dto = new FinancialValueDTO(uid(1), 5, 2025, 1000.0, uid(99), uid(2), uid(3), "Type", "Area", "Contract", FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(uid(99), uid(7))).thenReturn(Optional.empty());

        UUID id7 = uid(7);
        FinancialTypeNotFoundException ex = assertThrows(FinancialTypeNotFoundException.class, () -> {
            financialValueMapper.toEntity(dto, id7);
        });

        assertEquals("Tipo finanziario ID " + uid(99) + " non trovato", ex.getMessage());
    }

    @Test
    @DisplayName("Throw exception if business area belongs to another org (or does not exist)")
    void shouldThrowIfBusinessAreaNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(uid(1), 5, 2025, 1000.0, uid(1), uid(99), uid(3), "Type", "Area", "Contract", FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(uid(1), uid(7)))
                .thenReturn(Optional.of(FinancialTypes.builder().id(uid(1)).build()));
        when(businessAreaRepository.findByIdAndOrganizationId(uid(99), uid(7))).thenReturn(Optional.empty());

        UUID id7 = uid(7);
        BusinessAreaNotFoundException ex = assertThrows(BusinessAreaNotFoundException.class, () -> {
            financialValueMapper.toEntity(dto, id7);
        });

        assertEquals("Area di business ID " + uid(99) + " non trovata", ex.getMessage());
    }

    @Test
    @DisplayName("Throw exception if contract belongs to another org (or does not exist)")
    void shouldThrowIfContractNotFound() {
        FinancialValueDTO dto = new FinancialValueDTO(uid(1), 5, 2025, 1000.0, uid(1), uid(2), uid(99), "Type", "Area", "Contract", FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(uid(1), uid(7)))
                .thenReturn(Optional.of(FinancialTypes.builder().id(uid(1)).build()));
        when(businessAreaRepository.findByIdAndOrganizationId(uid(2), uid(7)))
                .thenReturn(Optional.of(BusinessAreas.builder().id(uid(2)).build()));
        when(contractsRepository.findByIdAndOrganization_Id(uid(99), uid(7))).thenReturn(Optional.empty());

        UUID id7 = uid(7);
        ContractNotFoundException ex = assertThrows(ContractNotFoundException.class, () -> {
            financialValueMapper.toEntity(dto, id7);
        });

        assertEquals("Contratto ID " + uid(99) + " non trovato", ex.getMessage());
    }

    /**
     * Tests that updateEntity updates all scalar and relation fields in-place.
     */
    @Test
    @DisplayName("updateEntity should update all fields including relations")
    void shouldUpdateEntityInPlace() {
        FinancialTypes newType = FinancialTypes.builder().id(uid(2)).build();
        BusinessAreas newArea = BusinessAreas.builder().id(uid(3)).build();
        Contracts newContract = Contracts.builder().id(uid(4)).build();

        FinancialValues existing = FinancialValues.builder()
                .id(uid(10))
                .month(1)
                .year(2024)
                .financialAmount(100.0)
                .financialType(FinancialTypes.builder().id(uid(1)).build())
                .businessArea(BusinessAreas.builder().id(uid(1)).build())
                .contract(Contracts.builder().id(uid(1)).build())
                .build();

        FinancialValueDTO dto = new FinancialValueDTO(uid(10), 6, 2025, 999.0, uid(2), uid(3), uid(4), "NewType", "NewArea", "NewContract", FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(uid(2), uid(7))).thenReturn(Optional.of(newType));
        when(businessAreaRepository.findByIdAndOrganizationId(uid(3), uid(7))).thenReturn(Optional.of(newArea));
        when(contractsRepository.findByIdAndOrganization_Id(uid(4), uid(7))).thenReturn(Optional.of(newContract));

        financialValueMapper.updateEntity(existing, dto, uid(7));

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
        FinancialValues existing = FinancialValues.builder().id(uid(1)).build();
        FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2025, 100.0, uid(99), uid(2), uid(3), null, null, null, FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(uid(99), uid(7))).thenReturn(Optional.empty());

        UUID id7 = uid(7);
        FinancialTypeNotFoundException ex = assertThrows(FinancialTypeNotFoundException.class,
                () -> financialValueMapper.updateEntity(existing, dto, id7));
        assertEquals("Tipo finanziario ID " + uid(99) + " non trovato", ex.getMessage());
    }

    @Test
    @DisplayName("updateEntity should throw if business area belongs to another org (or does not exist)")
    void shouldThrowOnUpdateIfBusinessAreaNotFound() {
        FinancialValues existing = FinancialValues.builder().id(uid(1)).build();
        FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2025, 100.0, uid(1), uid(99), uid(3), null, null, null, FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(uid(1), uid(7)))
                .thenReturn(Optional.of(FinancialTypes.builder().id(uid(1)).build()));
        when(businessAreaRepository.findByIdAndOrganizationId(uid(99), uid(7))).thenReturn(Optional.empty());

        UUID id7 = uid(7);
        BusinessAreaNotFoundException ex = assertThrows(BusinessAreaNotFoundException.class,
                () -> financialValueMapper.updateEntity(existing, dto, id7));
        assertEquals("Area di business ID " + uid(99) + " non trovata", ex.getMessage());
    }

    @Test
    @DisplayName("updateEntity should throw if contract belongs to another org (or does not exist)")
    void shouldThrowOnUpdateIfContractNotFound() {
        FinancialValues existing = FinancialValues.builder().id(uid(1)).build();
        FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2025, 100.0, uid(1), uid(2), uid(99), null, null, null, FinancialCategory.REVENUE);

        when(financialTypesRepository.findByIdAndOrganizationId(uid(1), uid(7)))
                .thenReturn(Optional.of(FinancialTypes.builder().id(uid(1)).build()));
        when(businessAreaRepository.findByIdAndOrganizationId(uid(2), uid(7)))
                .thenReturn(Optional.of(BusinessAreas.builder().id(uid(2)).build()));
        when(contractsRepository.findByIdAndOrganization_Id(uid(99), uid(7))).thenReturn(Optional.empty());

        UUID id7 = uid(7);
        ContractNotFoundException ex = assertThrows(ContractNotFoundException.class,
                () -> financialValueMapper.updateEntity(existing, dto, id7));
        assertEquals("Contratto ID " + uid(99) + " non trovato", ex.getMessage());
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
                .id(uid(1))
                .month(1)
                .year(2025)
                .financialAmount(1000.00)
                .financialType(null)
                .businessArea(BusinessAreas.builder().id(uid(1)).name("IT").build())
                .contract(Contracts.builder().id(uid(1))
                        .counterparty(Counterparty.builder().id(uid(1)).name("Client").type(CounterpartyType.CUSTOMER).build())
                        .build())
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
                .id(uid(1))
                .month(1)
                .year(2025)
                .financialAmount(1000.00)
                .financialType(FinancialTypes.builder().id(uid(1)).name("Revenue").build())
                .businessArea(null)
                .contract(Contracts.builder().id(uid(1))
                        .counterparty(Counterparty.builder().id(uid(1)).name("Client").type(CounterpartyType.CUSTOMER).build())
                        .build())
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
                .id(uid(1))
                .month(1)
                .year(2025)
                .financialAmount(1000.00)
                .financialType(FinancialTypes.builder().id(uid(1)).name("Revenue").build())
                .businessArea(BusinessAreas.builder().id(uid(1)).name("IT").build())
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
