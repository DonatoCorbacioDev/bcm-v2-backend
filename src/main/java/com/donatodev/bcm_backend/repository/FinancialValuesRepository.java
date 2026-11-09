/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialValues;

@Repository
public interface FinancialValuesRepository extends JpaRepository<FinancialValues, UUID> {

    List<FinancialValues> findByContractId(UUID contractId);

    List<FinancialValues> findByContract_Manager_Id(UUID managerId);

    List<FinancialValues> findAllByOrganizationId(UUID organizationId);

    List<FinancialValues> findByContractIdAndOrganizationId(UUID contractId, UUID organizationId);

    Optional<FinancialValues> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<FinancialValues> findByContract_IdAndFinancialType_Id(UUID contractId, UUID financialTypeId);

    @Query("""
            SELECT COALESCE(SUM(fv.financialAmount), 0.0)
            FROM FinancialValues fv
            WHERE fv.organization.id = :orgId
              AND fv.year = :year
              AND fv.month = :month
            """)
    double sumFinancialAmountByOrgAndYearMonth(
            @Param("orgId") UUID orgId,
            @Param("year") int year,
            @Param("month") int month);

    @Query("""
            SELECT COALESCE(SUM(fv.financialAmount), 0.0)
            FROM FinancialValues fv
            WHERE fv.organization.id = :orgId
              AND fv.businessArea.id = :areaId
              AND fv.financialType.category = :category
              AND fv.year = :year
            """)
    double sumAmountByOrgAreaCategoryYear(
            @Param("orgId") UUID orgId,
            @Param("areaId") UUID areaId,
            @Param("category") FinancialCategory category,
            @Param("year") int year);

    // Expected costs from January to :month — the same period as the
    // supplier invoices received so far, which are what they're compared
    // with. Revenue is left out: BCM doesn't handle invoices issued to customers.

    @Query("""
            SELECT COALESCE(SUM(fv.financialAmount), 0.0)
            FROM FinancialValues fv
            WHERE fv.organization.id = :orgId
              AND fv.financialType.category = com.donatodev.bcm_backend.entity.FinancialCategory.COST
              AND fv.year = :year AND fv.month <= :month
            """)
    double sumCostByOrgUpToMonth(@Param("orgId") UUID orgId, @Param("year") int year, @Param("month") int month);

    @Query("""
            SELECT COALESCE(SUM(fv.financialAmount), 0.0)
            FROM FinancialValues fv
            WHERE fv.financialType.category = com.donatodev.bcm_backend.entity.FinancialCategory.COST
              AND fv.year = :year AND fv.month <= :month
            """)
    double sumCostUpToMonth(@Param("year") int year, @Param("month") int month);

    @Query("""
            SELECT COALESCE(SUM(fv.financialAmount), 0.0)
            FROM FinancialValues fv
            WHERE fv.contract.manager.id = :managerId
              AND fv.financialType.category = com.donatodev.bcm_backend.entity.FinancialCategory.COST
              AND fv.year = :year AND fv.month <= :month
            """)
    double sumCostByManagerUpToMonth(@Param("managerId") UUID managerId, @Param("year") int year, @Param("month") int month);

    @Query("""
            SELECT COALESCE(SUM(fv.financialAmount), 0.0)
            FROM FinancialValues fv
            WHERE fv.contract.counterparty.id = :counterpartyId
              AND fv.financialType.category = com.donatodev.bcm_backend.entity.FinancialCategory.COST
              AND fv.year = :year AND fv.month <= :month
            """)
    double sumCostByCounterpartyUpToMonth(
            @Param("counterpartyId") UUID counterpartyId, @Param("year") int year, @Param("month") int month);
}
