package com.donatodev.bcm_backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Entity that maps the "financial_types" table in the database.
 * Defines the financial transaction categories (SALES, COSTS, NR, etc.)
 * associated with business contracts.
 */
@Entity
@Table(name = "financial_types")
@SuperBuilder
@NoArgsConstructor
public class FinancialTypes extends OrgNamedEntity {
}
