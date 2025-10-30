package com.donatodev.bcm_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a financial transaction type entity mapped to the {@code financial_types} table.
 * <p>
 * This entity defines various types of financial transactions related to business contracts.
 * Examples of financial types include:
 * <ul>
 *   <li><b>SALES</b>: Revenue-related transactions</li>
 *   <li><b>COSTS</b>: Expense-related transactions</li>
 *   <li><b>INVESTMENTS</b>: Capital investments</li>
 * </ul>
 */
@Entity
@Table(name = "financial_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialTypes {

    /**
     * Unique identifier for the financial type.
     * <p>
     * This field is auto-generated and acts as the primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the financial transaction type.
     * <p>
     * This field is required and must be unique.
     * Example values: {@code "SALES"}, {@code "COSTS"}, {@code "INVESTMENTS"}.
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Optional description providing more details about the financial type.
     */
    @Column(name = "description")
    private String description;
}
