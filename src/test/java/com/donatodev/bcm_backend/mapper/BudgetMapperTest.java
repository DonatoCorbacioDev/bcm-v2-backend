package com.donatodev.bcm_backend.mapper;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import com.donatodev.bcm_backend.config.TenantContext;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.BudgetDTO;
import com.donatodev.bcm_backend.entity.Budget;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.exception.BusinessAreaNotFoundException;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;

/**
 * Unit tests for {@link BudgetMapper}.
 * <p>
 * Verifies entity/DTO conversion and, in particular, the actual-amount and
 * percent-used computation resolved from {@code FinancialValues} at read time.
 */
@ActiveProfiles("test")
class BudgetMapperTest {

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

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
    private BusinessAreasRepository businessAreasRepository;

    @Mock
    private FinancialValuesRepository financialValuesRepository;

    @InjectMocks
    private BudgetMapper budgetMapper;

    @Nested
    class ToDTOTests {

        @Test
        @DisplayName("toDTO returns null when entity is null")
        void shouldReturnNullWhenEntityIsNull() {
            assertNull(budgetMapper.toDTO(null, uid(1)));
        }

        @Test
        @DisplayName("toDTO computes percent used from the actual amount")
        void shouldComputePercentUsed() {
            BusinessAreas area = BusinessAreas.builder().id(uid(2)).name("IT").build();
            Budget budget = Budget.builder().id(uid(1)).businessArea(area)
                    .category(FinancialCategory.COST).year(2025).targetAmount(1000.0).build();

            when(financialValuesRepository.sumAmountByOrgAreaCategoryYear(uid(9), uid(2), FinancialCategory.COST, 2025))
                    .thenReturn(400.0);

            BudgetDTO dto = budgetMapper.toDTO(budget, uid(9));

            assertEquals(uid(1), dto.id());
            assertEquals(uid(2), dto.businessAreaId());
            assertEquals("IT", dto.areaName());
            assertEquals(400.0, dto.actualAmount());
            assertEquals(40.0, dto.percentUsed());
        }

        @Test
        @DisplayName("toDTO reports 0 percent used when target amount is 0")
        void shouldReportZeroPercentWhenTargetIsZero() {
            BusinessAreas area = BusinessAreas.builder().id(uid(2)).name("IT").build();
            Budget budget = Budget.builder().id(uid(1)).businessArea(area)
                    .category(FinancialCategory.COST).year(2025).targetAmount(0.0).build();

            when(financialValuesRepository.sumAmountByOrgAreaCategoryYear(uid(9), uid(2), FinancialCategory.COST, 2025))
                    .thenReturn(400.0);

            BudgetDTO dto = budgetMapper.toDTO(budget, uid(9));

            assertEquals(0.0, dto.percentUsed());
        }
    }

    @Nested
    class ToEntityTests {

        @Test
        @DisplayName("toEntity resolves the business area from the repository")
        void shouldMapDTOToEntity() {
            BusinessAreas area = BusinessAreas.builder().id(uid(2)).name("IT").build();
            BudgetDTO dto = new BudgetDTO(uid(10), uid(2), "IT", FinancialCategory.REVENUE, 2025, 5000.0, 0, 0);

            when(businessAreasRepository.findByIdAndOrganizationId(uid(2), TENANT)).thenReturn(Optional.of(area));

            Budget entity = budgetMapper.toEntity(dto);

            assertEquals(uid(10), entity.getId());
            assertEquals(area, entity.getBusinessArea());
            assertEquals(FinancialCategory.REVENUE, entity.getCategory());
            assertEquals(2025, entity.getYear());
            assertEquals(5000.0, entity.getTargetAmount());
        }

        @Test
        @DisplayName("toEntity throws when the business area is not found")
        void shouldThrowIfBusinessAreaNotFound() {
            BudgetDTO dto = new BudgetDTO(null, uid(99), null, FinancialCategory.COST, 2025, 100.0, 0, 0);

            when(businessAreasRepository.findByIdAndOrganizationId(uid(99), TENANT)).thenReturn(Optional.empty());

            assertThrows(BusinessAreaNotFoundException.class, () -> budgetMapper.toEntity(dto));
        }

        @Test
        @DisplayName("updateEntity updates all fields including the business area")
        void shouldUpdateEntityInPlace() {
            BusinessAreas oldArea = BusinessAreas.builder().id(uid(1)).name("Old").build();
            BusinessAreas newArea = BusinessAreas.builder().id(uid(2)).name("New").build();
            Budget existing = Budget.builder().id(uid(1)).businessArea(oldArea)
                    .category(FinancialCategory.COST).year(2024).targetAmount(100.0).build();

            BudgetDTO dto = new BudgetDTO(uid(1), uid(2), "New", FinancialCategory.REVENUE, 2025, 200.0, 0, 0);

            when(businessAreasRepository.findByIdAndOrganizationId(uid(2), TENANT)).thenReturn(Optional.of(newArea));

            budgetMapper.updateEntity(existing, dto);

            assertEquals(newArea, existing.getBusinessArea());
            assertEquals(FinancialCategory.REVENUE, existing.getCategory());
            assertEquals(2025, existing.getYear());
            assertEquals(200.0, existing.getTargetAmount());
        }

        @Test
        @DisplayName("updateEntity throws when the business area is not found")
        void shouldThrowOnUpdateIfBusinessAreaNotFound() {
            Budget existing = Budget.builder().id(uid(1)).build();
            BudgetDTO dto = new BudgetDTO(uid(1), uid(99), null, FinancialCategory.COST, 2025, 100.0, 0, 0);

            when(businessAreasRepository.findByIdAndOrganizationId(uid(99), TENANT)).thenReturn(Optional.empty());

            assertThrows(BusinessAreaNotFoundException.class, () -> budgetMapper.updateEntity(existing, dto));
        }
    }
}
