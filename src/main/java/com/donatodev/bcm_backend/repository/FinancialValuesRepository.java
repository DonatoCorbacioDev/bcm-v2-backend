package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.FinancialValues;

@Repository
public interface FinancialValuesRepository extends JpaRepository<FinancialValues, Long> {

    List<FinancialValues> findByContractId(Long contractId);

    List<FinancialValues> findByContract_Manager_Id(Long managerId);

    List<FinancialValues> findAllByOrganizationId(Long organizationId);

    List<FinancialValues> findByContractIdAndOrganizationId(Long contractId, Long organizationId);

    @Query("""
            SELECT COALESCE(SUM(fv.financialAmount), 0.0)
            FROM FinancialValues fv
            WHERE fv.organization.id = :orgId
              AND fv.year = :year
              AND fv.month = :month
            """)
    double sumFinancialAmountByOrgAndYearMonth(
            @Param("orgId") Long orgId,
            @Param("year") int year,
            @Param("month") int month);
}
