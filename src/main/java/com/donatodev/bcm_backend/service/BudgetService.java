/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.BudgetDTO;
import com.donatodev.bcm_backend.entity.Budget;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.exception.BudgetNotFoundException;
import com.donatodev.bcm_backend.mapper.BudgetMapper;
import com.donatodev.bcm_backend.repository.BudgetRepository;

/**
 * Service class responsible for business logic related to budgets.
 * <p>
 * Provides methods to retrieve, create, update, and delete yearly
 * revenue/cost targets per business area.
 */
@Service
public class BudgetService {

    private static final String BUDGET_ID_PREFIX = "Budget ID ";
    private static final String NOT_FOUND_SUFFIX = " non trovato";

    private final BudgetRepository budgetRepository;
    private final BudgetMapper budgetMapper;

    public BudgetService(BudgetRepository budgetRepository, BudgetMapper budgetMapper) {
        this.budgetRepository = budgetRepository;
        this.budgetMapper = budgetMapper;
    }

    /**
     * Retrieves all budgets for the current tenant, each enriched with the
     * actual amount and percent used computed from financial values.
     *
     * @return a list of {@link BudgetDTO}
     */
    public List<BudgetDTO> getAllBudgets() {
        Long orgId = TenantContext.require();
        List<Budget> budgets = budgetRepository.findAllByOrganizationId(orgId);
        return budgets.stream().map(budget -> budgetMapper.toDTO(budget, orgId)).toList();
    }

    /**
     * Retrieves a budget by its ID.
     *
     * @param id the ID of the budget
     * @return the corresponding {@link BudgetDTO}
     * @throws BudgetNotFoundException if the budget is not found
     */
    public BudgetDTO getBudgetById(Long id) {
        Long orgId = TenantContext.require();
        Budget budget = findBudgetInScope(id)
                .orElseThrow(() -> new BudgetNotFoundException(BUDGET_ID_PREFIX + id + NOT_FOUND_SUFFIX));
        return budgetMapper.toDTO(budget, orgId);
    }

    /**
     * Finds a budget by ID, scoped to the current tenant when
     * {@link TenantContext} carries an organization ID.
     */
    private Optional<Budget> findBudgetInScope(Long id) {
        Long orgId = TenantContext.require();
        return budgetRepository.findByIdAndOrganizationId(id, orgId);
    }

    /**
     * Creates a new budget.
     *
     * @param dto the budget data transfer object
     * @return the created {@link BudgetDTO}
     */
    public BudgetDTO createBudget(BudgetDTO dto) {
        Budget budget = budgetMapper.toEntity(dto);
        // Creation must never merge an existing row selected by a client-supplied ID.
        budget.setId(null);
        Long orgId = TenantContext.require();
        Organization org = new Organization();
        org.setId(orgId);
        budget.setOrganization(org);
        budget = budgetRepository.save(budget);
        return budgetMapper.toDTO(budget, orgId);
    }

    /**
     * Updates an existing budget identified by ID.
     *
     * @param id  the ID of the budget to update
     * @param dto the updated budget data transfer object
     * @return the updated {@link BudgetDTO}
     * @throws BudgetNotFoundException if the budget is not found
     */
    public BudgetDTO updateBudget(Long id, BudgetDTO dto) {
        Long orgId = TenantContext.require();
        Budget budget = findBudgetInScope(id)
                .orElseThrow(() -> new BudgetNotFoundException(BUDGET_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        budgetMapper.updateEntity(budget, dto);

        budget = budgetRepository.save(budget);
        return budgetMapper.toDTO(budget, orgId);
    }

    /**
     * Deletes a budget by its ID.
     *
     * @param id the ID of the budget to delete
     * @throws BudgetNotFoundException if the budget is not found
     */
    public void deleteBudget(Long id) {
        Budget budget = findBudgetInScope(id)
                .orElseThrow(() -> new BudgetNotFoundException(BUDGET_ID_PREFIX + id + NOT_FOUND_SUFFIX));
        budgetRepository.delete(budget);
    }
}
