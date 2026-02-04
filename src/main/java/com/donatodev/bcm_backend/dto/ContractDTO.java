package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;

import com.donatodev.bcm_backend.entity.ContractStatus;

/**
 * Data Transfer Object for Contract.
 * <p>
 * Encapsulates all necessary data for contract-related operations in the REST
 * API. Includes nested manager and business area details for complete contract
 * information.
 *
 * @param id the unique identifier of the contract
 * @param customerName the name of the customer associated with the contract
 * @param contractNumber the contract reference number
 * @param wbsCode the WBS (Work Breakdown Structure) code for project tracking
 * @param projectName the name of the project
 * @param status the current status of the contract (e.g. ACTIVE, EXPIRED)
 * @param startDate the start date of the contract
 * @param endDate the end date of the contract
 * @param areaId the ID of the associated business area
 * @param managerId the ID of the assigned manager
 * @param managerName the name of the assigned manager
 * @param manager the nested manager details (optional)
 * @param area the nested business area details (optional)
 */
public record ContractDTO(
        Long id,
        String customerName,
        String contractNumber,
        String wbsCode,
        String projectName,
        ContractStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Long areaId,
        Long managerId,
        String managerName,
        ManagerDTO manager,
        BusinessAreaDTO area,
        Integer daysUntilExpiry
        ) {

}
