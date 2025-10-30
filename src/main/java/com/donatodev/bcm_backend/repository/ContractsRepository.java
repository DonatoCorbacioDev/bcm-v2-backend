package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;

/**
 * Repository interface for accessing {@link Contracts} entities.
 * <p>
 * Provides methods for retrieving contracts by status and manager.
 */
@Repository
public interface ContractsRepository extends JpaRepository<Contracts, Long> {

    /**
     * Finds all contracts with the specified status.
     *
     * @param status the contract status to filter by
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByStatus(ContractStatus status);

    /**
     * Finds all contracts assigned to a specific manager.
     *
     * @param managerId the ID of the manager
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByManagerId(Long managerId);

    /**
     * Finds all contracts assigned to a specific manager and having the specified status.
     *
     * @param managerId the ID of the manager
     * @param status    the contract status to filter by
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByManagerIdAndStatus(Long managerId, ContractStatus status);
    
    @Query("SELECT COUNT(c) FROM Contracts c")
    int countAllContracts();

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE")
    int countActiveContracts();

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.EXPIRED")
    int countExpiredContracts();

    @Query(value = """
              SELECT COUNT(*) FROM contracts 
              WHERE status = 'ACTIVE' 
                AND end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)
            """, nativeQuery = true)
    int countExpiringContracts();
    
    Page<Contracts> findAllBy(Pageable pageable);

    Page<Contracts> findByStatus(ContractStatus status, Pageable pageable);

    Page<Contracts> findByContractNumberContainingIgnoreCaseOrCustomerNameContainingIgnoreCase(
            String contractNumber, String customerName, Pageable pageable);

    Page<Contracts> findByStatusAndContractNumberContainingIgnoreCaseOrStatusAndCustomerNameContainingIgnoreCase(
            ContractStatus s1, String contractNumber,
            ContractStatus s2, String customerName,
            Pageable pageable);

    Page<Contracts> findByManagerId(Long managerId, Pageable pageable);

    Page<Contracts> findByManagerIdAndStatus(Long managerId, ContractStatus status, Pageable pageable);

    Page<Contracts> findByManagerIdAndContractNumberContainingIgnoreCaseOrManagerIdAndCustomerNameContainingIgnoreCase(
            Long managerId1, String contractNumber,
            Long managerId2, String customerName,
            Pageable pageable);

    Page<Contracts> findByManagerIdAndStatusAndContractNumberContainingIgnoreCaseOrManagerIdAndStatusAndCustomerNameContainingIgnoreCase(
            Long managerId1, ContractStatus status1, String contractNumber,
            Long managerId2, ContractStatus status2, String customerName,
            Pageable pageable);

}