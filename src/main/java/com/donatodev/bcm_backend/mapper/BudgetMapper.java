package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.BudgetDTO;
import com.donatodev.bcm_backend.entity.Budget;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.exception.BusinessAreaNotFoundException;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;

/**
 * Mapper class responsible for converting between {@link Budget} entities and
 * {@link BudgetDTO} data transfer objects.
 * <p>
 * Also resolves the actual amount spent/earned for a budget by summing the
 * matching {@code FinancialValues}, since that figure is never persisted.
 */
@Component
public class BudgetMapper {

    private final BusinessAreasRepository businessAreasRepository;
    private final FinancialValuesRepository financialValuesRepository;

    public BudgetMapper(
            BusinessAreasRepository businessAreasRepository,
            FinancialValuesRepository financialValuesRepository) {
        this.businessAreasRepository = businessAreasRepository;
        this.financialValuesRepository = financialValuesRepository;
    }

    /**
     * Converts a {@link Budget} entity to a {@link BudgetDTO}, resolving the
     * actual amount for its area/category/year from {@code FinancialValues}.
     *
     * @param budget the budget entity
     * @param orgId  the organization the budget belongs to, used to scope the actual-amount lookup
     * @return the corresponding DTO
     */
    public BudgetDTO toDTO(Budget budget, Long orgId) {
        if (budget == null) {
            return null;
        }

        double actualAmount = financialValuesRepository.sumAmountByOrgAreaCategoryYear(
                orgId,
                budget.getBusinessArea().getId(),
                budget.getCategory(),
                budget.getYear());

        double percentUsed = budget.getTargetAmount() == 0
                ? 0.0
                : (actualAmount / budget.getTargetAmount()) * 100.0;

        return new BudgetDTO(
                budget.getId(),
                budget.getBusinessArea().getId(),
                budget.getBusinessArea().getName(),
                budget.getCategory(),
                budget.getYear(),
                budget.getTargetAmount(),
                actualAmount,
                percentUsed
        );
    }

    /**
     * Converts a {@link BudgetDTO} to a {@link Budget} entity, resolving the
     * business area from the database.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     * @throws BusinessAreaNotFoundException if the referenced business area does not exist
     */
    public Budget toEntity(BudgetDTO dto) {
        BusinessAreas area = businessAreasRepository.findById(dto.businessAreaId())
                .orElseThrow(() -> new BusinessAreaNotFoundException("Business area ID " + dto.businessAreaId() + " not found"));

        return Budget.builder()
                .id(dto.id())
                .businessArea(area)
                .category(dto.category())
                .year(dto.year())
                .targetAmount(dto.targetAmount())
                .build();
    }

    /**
     * Updates an existing {@link Budget} entity in-place from a {@link BudgetDTO}.
     *
     * @param existing the entity to update
     * @param dto      the DTO with the new values
     * @throws BusinessAreaNotFoundException if the referenced business area does not exist
     */
    public void updateEntity(Budget existing, BudgetDTO dto) {
        BusinessAreas area = businessAreasRepository.findById(dto.businessAreaId())
                .orElseThrow(() -> new BusinessAreaNotFoundException("Business area ID " + dto.businessAreaId() + " not found"));

        existing.setBusinessArea(area);
        existing.setCategory(dto.category());
        existing.setYear(dto.year());
        existing.setTargetAmount(dto.targetAmount());
    }
}
