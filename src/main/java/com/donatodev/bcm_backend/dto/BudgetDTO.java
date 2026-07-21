package com.donatodev.bcm_backend.dto;

import com.donatodev.bcm_backend.entity.FinancialCategory;

/**
 * Data Transfer Object for Budget.
 * <p>
 * Represents a yearly revenue or cost target for a business area.
 * {@code actualAmount} and {@code percentUsed} are computed from the
 * matching financial values at read time, never persisted, and ignored
 * when the DTO is used as a create/update request body.
 *
 * @param id            the unique identifier of the budget
 * @param businessAreaId the ID of the related business area
 * @param areaName      the name of the business area
 * @param category      whether this budget targets revenue or cost
 * @param year          the year the budget applies to
 * @param targetAmount  the target amount set for the year
 * @param actualAmount  the sum of financial values matching area/category/year
 * @param percentUsed   actualAmount as a percentage of targetAmount (0 if targetAmount is 0)
 */
public record BudgetDTO(
        Long id,
        Long businessAreaId,
        String areaName,
        FinancialCategory category,
        int year,
        double targetAmount,
        double actualAmount,
        double percentUsed
) {}
