package com.donatodev.bcm_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Entity that maps the "financial_types" table in the database.
 * Defines the financial transaction categories (SALES, COSTS, NR, etc.)
 * associated with business contracts.
 */
@Entity
@Table(name = "financial_types")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class FinancialTypes extends OrgNamedEntity {

    /**
     * Whether this type represents revenue or a cost. Drives which
     * {@link FinancialValues} get summed into a {@link Budget}'s actual amount.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private FinancialCategory category;
}
