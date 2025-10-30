package com.donatodev.bcm_backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a company contract entity stored in the "contracts" table.
 * This entity holds all relevant information regarding a contract such as
 * customer, project, associated business area, manager, and contract status.
 * <p>
 * Each contract can optionally be linked to a manager and must belong to a business area.
 * The contract status is represented as an enum and stored as a string in the database.
 * </p>
 */
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
     * Name of the customer associated with the contract.
     * Cannot be null.
     */
    @Column(name = "customer_name", nullable = false)
    private String customerName;

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
    private LocalDateTime createdAt = LocalDateTime.now();
}
