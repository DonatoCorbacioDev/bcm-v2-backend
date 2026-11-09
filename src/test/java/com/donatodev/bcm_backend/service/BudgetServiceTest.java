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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.BudgetDTO;
import com.donatodev.bcm_backend.entity.Budget;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.exception.BudgetNotFoundException;
import com.donatodev.bcm_backend.mapper.BudgetMapper;
import com.donatodev.bcm_backend.repository.BudgetRepository;

/**
 * Unit tests for {@link BudgetService}.
 * <p>
 * Verifies the correct behavior of the service methods for managing
 * {@link Budget}, including retrieval, creation, updating and deletion. The
 * repository and mapper are mocked.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class BudgetServiceTest {

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
    private BudgetRepository budgetRepository;

    @Mock
    private BudgetMapper budgetMapper;

    @InjectMocks
    private BudgetService budgetService;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: BudgetService")
    @SuppressWarnings("unused")
    class VerifyBudgetService {

        @Test
        @Order(1)
        @DisplayName("Get all budgets returns list of DTOs")
        void shouldGetAllBudgets() {
            Budget entity = Budget.builder().id(uid(1)).category(FinancialCategory.COST).year(2025).targetAmount(1000.0).build();
            BudgetDTO dto = new BudgetDTO(uid(1), uid(2), "IT", FinancialCategory.COST, 2025, 1000.0, 400.0, 40.0);

            when(budgetRepository.findAllByOrganizationId(TENANT)).thenReturn(List.of(entity));
            when(budgetMapper.toDTO(entity, TENANT)).thenReturn(dto);

            List<BudgetDTO> result = budgetService.getAllBudgets();

            assertEquals(1, result.size());
            assertEquals("IT", result.get(0).areaName());
        }

        @Test
        @Order(2)
        @DisplayName("Get budget by ID returns DTO")
        void shouldGetBudgetById() {
            Budget entity = Budget.builder().id(uid(1)).category(FinancialCategory.REVENUE).year(2025).targetAmount(500.0).build();
            BudgetDTO dto = new BudgetDTO(uid(1), uid(2), "Sales", FinancialCategory.REVENUE, 2025, 500.0, 250.0, 50.0);

            when(budgetRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(budgetMapper.toDTO(entity, TENANT)).thenReturn(dto);

            BudgetDTO result = budgetService.getBudgetById(uid(1));

            assertEquals("Sales", result.areaName());
        }

        @Test
        @Order(3)
        @DisplayName("Get budget by ID throws if not found")
        void shouldThrowWhenBudgetNotFound() {
            when(budgetRepository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            BudgetNotFoundException ex =
                assertThrows(BudgetNotFoundException.class, () -> budgetService.getBudgetById(uid(999)));
            assertEquals("Budget ID " + uid(999) + " non trovato", ex.getMessage());
        }

        @Test
        @Order(4)
        @DisplayName("Create budget returns saved DTO")
        void shouldCreateBudget() {
            BudgetDTO dto = new BudgetDTO(null, uid(2), "IT", FinancialCategory.COST, 2025, 1000.0, 0, 0);
            Budget entity = Budget.builder().category(FinancialCategory.COST).year(2025).targetAmount(1000.0).build();
            Budget saved = Budget.builder().id(uid(1)).category(FinancialCategory.COST).year(2025).targetAmount(1000.0).build();
            BudgetDTO savedDTO = new BudgetDTO(uid(1), uid(2), "IT", FinancialCategory.COST, 2025, 1000.0, 0, 0);

            when(budgetMapper.toEntity(dto)).thenReturn(entity);
            when(budgetRepository.save(entity)).thenReturn(saved);
            when(budgetMapper.toDTO(saved, TENANT)).thenReturn(savedDTO);

            BudgetDTO result = budgetService.createBudget(dto);

            assertEquals(uid(1), result.id());
            assertEquals("IT", result.areaName());
        }

        @Test
        @Order(5)
        @DisplayName("Update budget returns updated DTO")
        void shouldUpdateBudget() {
            Budget existing = Budget.builder().id(uid(1)).category(FinancialCategory.COST).year(2024).targetAmount(500.0).build();
            BudgetDTO updateDTO = new BudgetDTO(uid(1), uid(2), "IT", FinancialCategory.COST, 2025, 1500.0, 0, 0);
            Budget updatedEntity = Budget.builder().id(uid(1)).category(FinancialCategory.COST).year(2025).targetAmount(1500.0).build();

            when(budgetRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(existing));
            when(budgetRepository.save(existing)).thenReturn(updatedEntity);
            when(budgetMapper.toDTO(updatedEntity, TENANT)).thenReturn(updateDTO);

            BudgetDTO result = budgetService.updateBudget(uid(1), updateDTO);

            assertEquals(2025, result.year());
            assertEquals(1500.0, result.targetAmount());
            verify(budgetMapper).updateEntity(existing, updateDTO);
        }

        @Test
        @Order(6)
        @DisplayName("Update budget throws if not found")
        void shouldThrowWhenUpdatingMissingBudget() {
            BudgetDTO updateDTO = new BudgetDTO(uid(999), uid(2), "IT", FinancialCategory.COST, 2025, 100.0, 0, 0);
            when(budgetRepository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            BudgetNotFoundException ex =
                assertThrows(BudgetNotFoundException.class, () -> budgetService.updateBudget(uid(999), updateDTO));
            assertEquals("Budget ID " + uid(999) + " non trovato", ex.getMessage());
        }

        @Test
        @Order(7)
        @DisplayName("Delete budget calls repository")
        void shouldDeleteBudget() {
            Budget entity = Budget.builder().id(uid(1)).build();
            when(budgetRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));

            budgetService.deleteBudget(uid(1));

            verify(budgetRepository, times(1)).delete(entity);
        }

        @Test
        @Order(8)
        @DisplayName("Delete budget throws if not found")
        void shouldThrowWhenDeletingMissingBudget() {
            when(budgetRepository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            BudgetNotFoundException ex = assertThrows(BudgetNotFoundException.class,
                    () -> budgetService.deleteBudget(uid(999)));

            assertEquals("Budget ID " + uid(999) + " non trovato", ex.getMessage());
            verify(budgetRepository, never()).delete(any(Budget.class));
        }

        @Test
        @Order(9)
        @DisplayName("getAllBudgets with TenantContext uses org-filtered repository")
        void shouldGetAllBudgetsWithTenantContext() {
            Budget entity = Budget.builder().id(uid(1)).build();
            BudgetDTO dto = new BudgetDTO(uid(1), uid(2), "IT", FinancialCategory.COST, 2025, 1000.0, 0, 0);

            TenantContext.set(uid(5));
            try {
                when(budgetRepository.findAllByOrganizationId(uid(5))).thenReturn(List.of(entity));
                when(budgetMapper.toDTO(entity, uid(5))).thenReturn(dto);

                List<BudgetDTO> result = budgetService.getAllBudgets();

                assertEquals(1, result.size());
                verify(budgetRepository).findAllByOrganizationId(uid(5));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(10)
        @DisplayName("createBudget with TenantContext sets organization on entity")
        void shouldCreateBudgetWithTenantContext() {
            BudgetDTO dto = new BudgetDTO(null, uid(2), "IT", FinancialCategory.COST, 2025, 1000.0, 0, 0);
            Budget entity = Budget.builder().category(FinancialCategory.COST).year(2025).targetAmount(1000.0).build();
            Budget saved = Budget.builder().id(uid(3)).category(FinancialCategory.COST).year(2025).targetAmount(1000.0).build();
            BudgetDTO savedDTO = new BudgetDTO(uid(3), uid(2), "IT", FinancialCategory.COST, 2025, 1000.0, 0, 0);

            TenantContext.set(uid(7));
            try {
                when(budgetMapper.toEntity(dto)).thenReturn(entity);
                when(budgetRepository.save(any())).thenReturn(saved);
                when(budgetMapper.toDTO(saved, uid(7))).thenReturn(savedDTO);

                BudgetDTO result = budgetService.createBudget(dto);

                assertEquals(uid(3), result.id());
                assertNotNull(entity.getOrganization());
                assertEquals(uid(7), entity.getOrganization().getId());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(11)
        @DisplayName("getBudgetById with TenantContext uses org-scoped repository")
        void shouldGetBudgetByIdWithTenantContext() {
            Budget entity = Budget.builder().id(uid(1)).build();
            BudgetDTO dto = new BudgetDTO(uid(1), uid(2), "IT", FinancialCategory.COST, 2025, 1000.0, 0, 0);

            TenantContext.set(uid(9));
            try {
                when(budgetRepository.findByIdAndOrganizationId(uid(1), uid(9))).thenReturn(Optional.of(entity));
                when(budgetMapper.toDTO(entity, uid(9))).thenReturn(dto);

                BudgetDTO result = budgetService.getBudgetById(uid(1));

                assertEquals("IT", result.areaName());
                verify(budgetRepository).findByIdAndOrganizationId(uid(1), uid(9));
            } finally {
                TenantContext.clear();
            }
        }
    }
}
