/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a company contract entity stored in the "contracts" table.
 * This entity holds all relevant information regarding a contract such as
 * counterparty, project, associated business area, manager, and contract status.
 * <p>
 * Each contract can optionally be linked to a manager and must belong to a business area.
 * The contract status is represented as an enum and stored as a string in the database.
 * </p>
 */
@NamedEntityGraph(
    name = "contracts.withManagerAndArea",
    attributeNodes = {
        @NamedAttributeNode("manager"),
        @NamedAttributeNode("businessArea"),
        @NamedAttributeNode("counterparty")
    }
)
@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contracts {

    /**
     * Primary key - Unique identifier for each contract.
     * Auto-generated using the database's identity strategy.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The company this contract is with — a customer, a supplier, or both.
     * Cannot be null.
     */
    @ManyToOne
    @JoinColumn(name = "counterparty_id", nullable = false)
    private Counterparty counterparty;

    /**
     * Unique identifier number for the contract.
     * Must be unique and not null.
     */
    @Column(name = "contract_number", nullable = false, unique = true)
    private String contractNumber;

    /**
     * Optional WBS (Work Breakdown Structure) code for project tracking.
     */
    @Column(name = "wbs_code")
    private String wbsCode;

    /**
     * Optional project name associated with the contract.
     */
    @Column(name = "project_name")
    private String projectName;

    /**
     * Reference to the business area to which this contract belongs.
     * Cannot be null.
     */
    @ManyToOne
    @JoinColumn(name = "area_id", nullable = false)
    private BusinessAreas businessArea;

    /**
     * Optional reference to the manager assigned to this contract.
     * This field can be null if no manager is assigned.
     */
    @ManyToOne
    @JoinColumn(name = "manager_id")
    private Managers manager;

    /**
     * Start date of the contract.
     * This field is mandatory.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Optional end date of the contract.
     * Can be null for ongoing contracts.
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Status of the contract (e.g., ACTIVE, CANCELLED, EXPIRED).
     * Stored as a string in the database.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContractStatus status;

    /**
     * Timestamp when the contract was created.
     * Automatically set at creation and cannot be updated later.
     */
    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.systemDefault());

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /**
     * Approval workflow stage. Only meaningful while {@link #status} is
     * {@link ContractStatus#DRAFT}; {@code null} for contracts created
     * directly as ACTIVE/EXPIRED/CANCELLED, which never enter the workflow.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_stage")
    private WorkflowStage workflowStage;

    /**
     * Optional financial terms — when {@link #financialType}, {@link #annualValue}
     * and {@link #billingFrequency} are all set, {@code ContractFinancialGenerationService}
     * auto-generates the contract's {@link FinancialValues} rows instead of
     * requiring them to be entered one at a time. A contract with none of
     * these set behaves exactly as before (fully manual entry).
     */
    @ManyToOne
    @JoinColumn(name = "financial_type_id")
    private FinancialTypes financialType;

    /** The contract's yearly value in euros; divided by {@link BillingFrequency#getPeriodsPerYear()} for each generated row. */
    @Column(name = "annual_value")
    private Double annualValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_frequency")
    private BillingFrequency billingFrequency;
}
