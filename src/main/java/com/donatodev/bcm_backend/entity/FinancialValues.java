package com.donatodev.bcm_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the {@code financial_values} table in the database.
 * <p>
 * This entity stores financial transaction records associated with specific contracts, business areas,
 * and financial types. Each record includes the month, year, amount, and relational links.
 */
@Entity
@Table(name = "financial_values")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialValues {

    /**
     * Unique identifier for the financial value entry.
     * This ID is auto-generated and serves as the primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Month of the financial transaction (1 to 12).
     * This field is required.
     */
    @Column(name = "month_value", nullable = false)
    private int month;

    /**
     * Year of the financial transaction (e.g., 2024).
     * This field is required.
     */
    @Column(name = "year_value", nullable = false)
    private int year;

    /**
     * Amount of the financial transaction.
     * This field is required and represents the monetary value recorded.
     */
    @Column(name = "financial_amount", nullable = false)
    private double financialAmount;

    /**
     * The financial type associated with this transaction.
     * Represents categories such as SALES, COSTS, or INVESTMENTS.
     * <p>
     * Many financial values can share the same financial type.
     */
    @ManyToOne
    @JoinColumn(name = "financial_type_id", nullable = false)
    private FinancialTypes financialType;

    /**
     * The business area to which this financial value belongs.
     * <p>
     * Links the transaction to a specific organizational unit.
     */
    @ManyToOne
    @JoinColumn(name = "area_id", nullable = false)
    private BusinessAreas businessArea;

    /**
     * The contract associated with this financial value.
     * <p>
     * Every financial value must be linked to a specific contract.
     */
    @ManyToOne
    @JoinColumn(name = "contract_id", nullable = false)
    private Contracts contract;
}
