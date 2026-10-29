/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.BillingFrequency;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.WorkflowStage;

/** One element of {@code data/contracts.json}. */
public record ContractExportDTO(
        Long id,
        String contractNumber,
        String wbsCode,
        String projectName,
        Long counterpartyId,
        Long businessAreaId,
        Long managerId,
        Long financialTypeId,
        LocalDate startDate,
        LocalDate endDate,
        ContractStatus status,
        WorkflowStage workflowStage,
        Double annualValue,
        BillingFrequency billingFrequency,
        LocalDateTime createdAt) {

    public static ContractExportDTO from(Contracts contract) {
        return new ContractExportDTO(
                contract.getId(),
                contract.getContractNumber(),
                contract.getWbsCode(),
                contract.getProjectName(),
                contract.getCounterparty().getId(),
                contract.getBusinessArea().getId(),
                ExportIds.idOf(contract.getManager(), Managers::getId),
                ExportIds.idOf(contract.getFinancialType(), FinancialTypes::getId),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getStatus(),
                contract.getWorkflowStage(),
                contract.getAnnualValue(),
                contract.getBillingFrequency(),
                contract.getCreatedAt());
    }
}
