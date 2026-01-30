package com.donatodev.bcm_backend.dto;

/**
 * Data Transfer Object for Financial Values.
 * <p>
 * Represents a monetary value associated with a specific financial type,
 * business area, and contract for a given month and year.
 *
 * @param id the unique identifier of the financial value
 * @param month the month (1-12) to which the value refers
 * @param year the year to which the value refers
 * @param financialAmount the financial amount (e.g., revenue or cost)
 * @param financialTypeId the ID of the financial type (e.g., Revenue, Cost)
 * @param businessAreaId the ID of the related business area
 * @param contractId the ID of the associated contract
 * @param typeName the name of the financial type
 * @param areaName the name of the business area
 * @param customerName the name of the customer
 */
public record FinancialValueDTO(
        Long id,
        int month,
        int year,
        double financialAmount,
        Long financialTypeId,
        Long businessAreaId,
        Long contractId,
        String typeName,
        String areaName,
        String customerName
        ) {

}
