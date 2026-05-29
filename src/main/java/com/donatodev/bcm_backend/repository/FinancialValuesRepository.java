package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.FinancialValues;

@Repository
public interface FinancialValuesRepository extends JpaRepository<FinancialValues, Long> {

    List<FinancialValues> findByContractId(Long contractId);

    List<FinancialValues> findByContract_Manager_Id(Long managerId);

    List<FinancialValues> findAllByOrganizationId(Long organizationId);

    List<FinancialValues> findByContractIdAndOrganizationId(Long contractId, Long organizationId);
}
