package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.BudgetDTO;
import com.donatodev.bcm_backend.service.BudgetService;

/**
 * REST controller for managing Budgets.
 * <p>
 * Provides endpoints for creating, retrieving, updating and deleting yearly
 * revenue/cost targets per business area.
 */
@RestController
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    /**
     * Retrieves all budgets for the current organization.
     *
     * @return a list of {@link BudgetDTO}
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping
    public ResponseEntity<List<BudgetDTO>> getAllBudgets() {
        List<BudgetDTO> budgets = budgetService.getAllBudgets();
        return ResponseEntity.ok(budgets);
    }

    /**
     * Retrieves a single budget by its ID.
     *
     * @param id the ID of the budget
     * @return the {@link BudgetDTO} with the specified ID
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<BudgetDTO> getBudgetById(@PathVariable Long id) {
        BudgetDTO budget = budgetService.getBudgetById(id);
        return ResponseEntity.ok(budget);
    }

    /**
     * Creates a new budget.
     *
     * @param dto the data for the new budget
     * @return the created {@link BudgetDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<BudgetDTO> createBudget(@RequestBody BudgetDTO dto) {
        BudgetDTO newBudget = budgetService.createBudget(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(newBudget);
    }

    /**
     * Updates an existing budget by its ID.
     *
     * @param id  the ID of the budget to update
     * @param dto the updated data
     * @return the updated {@link BudgetDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<BudgetDTO> updateBudget(@PathVariable Long id, @RequestBody BudgetDTO dto) {
        BudgetDTO updatedBudget = budgetService.updateBudget(id, dto);
        return ResponseEntity.ok(updatedBudget);
    }

    /**
     * Deletes a budget by its ID.
     *
     * @param id the ID of the budget to delete
     * @return HTTP 204 No Content if successful
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable Long id) {
        budgetService.deleteBudget(id);
        return ResponseEntity.noContent().build();
    }
}
