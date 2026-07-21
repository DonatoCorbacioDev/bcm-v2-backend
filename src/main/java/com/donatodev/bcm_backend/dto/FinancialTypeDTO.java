package com.donatodev.bcm_backend.dto;

import com.donatodev.bcm_backend.entity.FinancialCategory;

/**
 * Data Transfer Object for Financial Types.
 * <p>
 * Represents the category or type of financial data associated with a contract.
 *
 * @param id          the unique identifier of the financial type
 * @param name        the name of the financial type (e.g., Revenue, Cost)
 * @param description a brief description of the financial type
 * @param category    whether this type represents revenue or a cost
 */
public record FinancialTypeDTO(
        Long id,
        String name,
        String description,
        FinancialCategory category
) {}
