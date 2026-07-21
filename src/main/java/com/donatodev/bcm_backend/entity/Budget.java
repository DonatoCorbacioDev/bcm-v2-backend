package com.donatodev.bcm_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the {@code budgets} table: a yearly revenue or cost target for a
 * business area, compared against the sum of {@link FinancialValues} sharing
 * the same area, category and year.
 */
@Entity
@Table(name = "budgets", uniqueConstraints = @UniqueConstraint(
        name = "uq_budget_area_category_year_org",
        columnNames = {"business_area_id", "category", "year_value", "organization_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "business_area_id", nullable = false)
    private BusinessAreas businessArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private FinancialCategory category;

    @Column(name = "year_value", nullable = false)
    private int year;

    @Column(name = "target_amount", nullable = false)
    private double targetAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "organization_id")
    private Organization organization;
}
