package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.FinancialValues;

/**
 * Repository interface for accessing {@link FinancialValues} entities.
 * <p>
 * Provides methods to retrieve financial values based on contract or manager.
 */
@Repository
public interface FinancialValuesRepository extends JpaRepository<FinancialValues, Long> {

    /**
     * Retrieves all financial values associated with a specific contract.
     *
     * @param contractId the ID of the contract
     * @return a list of matching {@link FinancialValues}
     */
    List<FinancialValues> findByContractId(Long contractId);

    /**
     * Retrieves all financial values where the contract is assigned to a specific manager.
     *
     * @param managerId the ID of the manager
     * @return a list of matching {@link FinancialValues}
     */
    List<FinancialValues> findByContract_Manager_Id(Long managerId);
}
