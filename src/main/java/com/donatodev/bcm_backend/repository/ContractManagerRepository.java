package com.donatodev.bcm_backend.repository;

import com.donatodev.bcm_backend.entity.ContractManager;
import com.donatodev.bcm_backend.entity.ContractManagerId;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository interface for accessing {@link ContractManager} entities,
 * which represent the relationship between contracts and managers.
 * <p>
 * Uses {@link ContractManagerId} as a composite primary key.
 */
@Repository
public interface ContractManagerRepository extends JpaRepository<ContractManager, ContractManagerId> {
	
	@Query(value = "SELECT manager_id FROM contract_manager WHERE contract_id = :contractId", nativeQuery = true)
    List<Long> findManagerIdsByContractId(Long contractId);

    @Modifying 
    @Transactional
    @Query(value = "DELETE FROM contract_manager WHERE contract_id = :contractId", nativeQuery = true)
    void deleteAllByContractId(Long contractId);

    @Modifying 
    @Transactional
    @Query(value = "INSERT IGNORE INTO contract_manager (contract_id, manager_id) VALUES (:contractId, :managerId)", nativeQuery = true)
    void insertIgnore(Long contractId, Long managerId);
}
