package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;

import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.WorkflowStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
 * @param workflowStage the approval workflow stage (null if the contract never entered the workflow)
 */
public record ContractDTO(
        Long id,
        @NotBlank(message = "Customer name is required") String customerName,
        @NotBlank(message = "Contract number is required") String contractNumber,
        String wbsCode,
        String projectName,
        ContractStatus status,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "End date is required") LocalDate endDate,
        @NotNull(message = "Business area is required") Long areaId,
        Long managerId,
        String managerName,
        ManagerDTO manager,
        BusinessAreaDTO area,
        Integer daysUntilExpiry,
        WorkflowStage workflowStage
        ) {

    /**
     * Compatibility constructor for call sites predating the approval
     * workflow — defaults {@code workflowStage} to {@code null} (contract
     * not part of the workflow).
     */
    public ContractDTO(Long id, String customerName, String contractNumber, String wbsCode,
            String projectName, ContractStatus status, LocalDate startDate, LocalDate endDate,
            Long areaId, Long managerId, String managerName, ManagerDTO manager, BusinessAreaDTO area,
            Integer daysUntilExpiry) {
        this(id, customerName, contractNumber, wbsCode, projectName, status, startDate, endDate,
                areaId, managerId, managerName, manager, area, daysUntilExpiry, null);
    }
}
