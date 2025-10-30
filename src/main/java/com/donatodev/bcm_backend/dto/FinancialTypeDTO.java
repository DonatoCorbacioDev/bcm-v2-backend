package com.donatodev.bcm_backend.dto;

/**
 * Data Transfer Object for Financial Types.
 * <p>
 * Represents the category or type of financial data associated with a contract.
 *
 * @param id          the unique identifier of the financial type
 * @param name        the name of the financial type (e.g., Revenue, Cost)
 * @param description a brief description of the financial type
 */
public record FinancialTypeDTO(
        Long id,
        String name,
        String description
) {}
