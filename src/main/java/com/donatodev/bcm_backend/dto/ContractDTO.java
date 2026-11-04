/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;

import com.donatodev.bcm_backend.entity.BillingFrequency;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.WorkflowStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * @param counterpartyId the ID of the counterparty (customer/supplier) this contract is with
 * @param counterparty the nested counterparty details (optional)
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
 * @param financialTypeId the financial type generated {@code FinancialValues} rows should use (optional — see financial terms below)
 * @param annualValue the contract's yearly value in euros, divided across periods per {@code billingFrequency} (optional)
 * @param billingFrequency how often the contract invoices; together with {@code financialTypeId}/{@code annualValue}, opts a contract into automatic financial-value generation (optional)
 * @param autoRenew whether the contract renews tacitly (null = not stated)
 * @param noticeDays days of notice required to stop the tacit renewal (optional)
 * @param renewalMonths length of each tacit renewal, in months (optional)
 * @param noticeDeadline read-only: last day to give notice, {@code endDate - noticeDays}; null unless both terms are stated
 * @param daysUntilNoticeDeadline read-only: days left until {@code noticeDeadline}, for ACTIVE contracts
 */
public record ContractDTO(
        Long id,
        @NotNull(message = "Controparte obbligatoria") Long counterpartyId,
        CounterpartyDTO counterparty,
        @NotBlank(message = "Numero contratto obbligatorio") String contractNumber,
        String wbsCode,
        String projectName,
        ContractStatus status,
        @NotNull(message = "Data inizio obbligatoria") LocalDate startDate,
        @NotNull(message = "Data fine obbligatoria") LocalDate endDate,
        @NotNull(message = "Area aziendale obbligatoria") Long areaId,
        Long managerId,
        String managerName,
        ManagerDTO manager,
        BusinessAreaDTO area,
        Integer daysUntilExpiry,
        WorkflowStage workflowStage,
        Long financialTypeId,
        Double annualValue,
        BillingFrequency billingFrequency,
        Boolean autoRenew,
        @Min(value = 0, message = "Il preavviso non può essere negativo")
        @Max(value = 3650, message = "Il preavviso non può superare i 10 anni") Integer noticeDays,
        @Min(value = 1, message = "La durata del rinnovo deve essere di almeno 1 mese")
        @Max(value = 120, message = "La durata del rinnovo non può superare i 120 mesi") Integer renewalMonths,
        LocalDate noticeDeadline,
        Integer daysUntilNoticeDeadline
        ) {

    /**
     * Compatibility constructor for call sites predating tacit renewal terms —
     * defaults them to {@code null} ("not stated").
     */
    public ContractDTO(Long id, Long counterpartyId, CounterpartyDTO counterparty, String contractNumber, String wbsCode,
            String projectName, ContractStatus status, LocalDate startDate, LocalDate endDate,
            Long areaId, Long managerId, String managerName, ManagerDTO manager, BusinessAreaDTO area,
            Integer daysUntilExpiry, WorkflowStage workflowStage, Long financialTypeId, Double annualValue,
            BillingFrequency billingFrequency) {
        this(id, counterpartyId, counterparty, contractNumber, wbsCode, projectName, status, startDate, endDate,
                areaId, managerId, managerName, manager, area, daysUntilExpiry, workflowStage, financialTypeId,
                annualValue, billingFrequency, null, null, null, null, null);
    }

    /**
     * Compatibility constructor for call sites predating optional financial
     * terms on a contract — defaults {@code financialTypeId}/{@code annualValue}/
     * {@code billingFrequency} to {@code null} (contract not auto-generating
     * its financial values).
     */
    public ContractDTO(Long id, Long counterpartyId, CounterpartyDTO counterparty, String contractNumber, String wbsCode,
            String projectName, ContractStatus status, LocalDate startDate, LocalDate endDate,
            Long areaId, Long managerId, String managerName, ManagerDTO manager, BusinessAreaDTO area,
            Integer daysUntilExpiry, WorkflowStage workflowStage) {
        this(id, counterpartyId, counterparty, contractNumber, wbsCode, projectName, status, startDate, endDate,
                areaId, managerId, managerName, manager, area, daysUntilExpiry, workflowStage, null, null, null);
    }

    /**
     * Compatibility constructor for call sites predating the approval
     * workflow — defaults {@code workflowStage} (and the financial-terms
     * fields) to {@code null}.
     */
    public ContractDTO(Long id, Long counterpartyId, CounterpartyDTO counterparty, String contractNumber, String wbsCode,
            String projectName, ContractStatus status, LocalDate startDate, LocalDate endDate,
            Long areaId, Long managerId, String managerName, ManagerDTO manager, BusinessAreaDTO area,
            Integer daysUntilExpiry) {
        this(id, counterpartyId, counterparty, contractNumber, wbsCode, projectName, status, startDate, endDate,
                areaId, managerId, managerName, manager, area, daysUntilExpiry, null);
    }
}
