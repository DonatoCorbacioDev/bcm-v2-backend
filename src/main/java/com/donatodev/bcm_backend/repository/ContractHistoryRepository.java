package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.ContractHistory;

/**
 * Repository interface for accessing {@link ContractHistory} entities.
 * <p>
 * Provides standard CRUD operations and a method to retrieve history entries
 * related to a specific contract.
 */
@Repository
public interface ContractHistoryRepository extends JpaRepository<ContractHistory, Long> {

    /**
     * Retrieves all contract history entries for a specific contract ID.
     *
     * @param contractId the ID of the contract
     * @return a list of {@link ContractHistory} records
     */
    List<ContractHistory> findByContractId(Long contractId);
}
