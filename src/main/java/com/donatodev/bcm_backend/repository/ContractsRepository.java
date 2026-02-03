package com.donatodev.bcm_backend.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.dto.ContractsByAreaDTO;
import com.donatodev.bcm_backend.dto.TopManagerDTO;
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
     * Finds all contracts assigned to a specific manager and having the
     * specified status.
     *
     * @param managerId the ID of the manager
     * @param status the contract status to filter by
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByManagerIdAndStatus(Long managerId, ContractStatus status);

    @Query("SELECT COUNT(c) FROM Contracts c")
    int countAllContracts();

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE")
    int countActiveContracts();

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.EXPIRED")
    int countExpiredContracts();

    @Query("""
          SELECT COUNT(c) FROM Contracts c 
          WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE 
            AND c.endDate BETWEEN CURRENT_DATE AND :endDate
        """)
    int countExpiringContracts(@Param("endDate") LocalDate endDate);

    /**
     * Finds all ACTIVE contracts that will expire between today and a future
     * date.
     *
     * @param today the current date
     * @param futureDate the end date for the expiry window
     * @return a list of expiring {@link Contracts}
     */
    @Query("""
        SELECT c FROM Contracts c 
        WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE 
          AND c.endDate BETWEEN :today AND :futureDate
        ORDER BY c.endDate ASC
      """)
    List<Contracts> findExpiringContracts(@Param("today") LocalDate today, @Param("futureDate") LocalDate futureDate);

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

    /**
     * Count contracts grouped by business area.
     *
     * @return list of business area names with contract counts
     */
    @Query("""
        SELECT new com.donatodev.bcm_backend.dto.ContractsByAreaDTO(
            ba.name, 
            COUNT(c.id)
        )
        FROM Contracts c
        JOIN c.businessArea ba
        GROUP BY ba.id, ba.name
        ORDER BY COUNT(c.id) DESC
    """)
    List<ContractsByAreaDTO> countContractsByArea();

    /**
     * Count contracts created per month for the last 6 months. Uses HQL with
     * EXTRACT functions for cross-database compatibility.
     *
     * @param sixMonthsAgo the date 6 months ago
     * @return list of Object arrays containing [year, month, count]
     */
    @Query("""
    SELECT 
        EXTRACT(YEAR FROM c.createdAt),
        EXTRACT(MONTH FROM c.createdAt),
        COUNT(c)
    FROM Contracts c
    WHERE c.createdAt >= :sixMonthsAgo
    GROUP BY EXTRACT(YEAR FROM c.createdAt), EXTRACT(MONTH FROM c.createdAt)
    ORDER BY EXTRACT(YEAR FROM c.createdAt), EXTRACT(MONTH FROM c.createdAt)
""")
    List<Object[]> countContractsByMonth(@Param("sixMonthsAgo") LocalDateTime sixMonthsAgo);

    /**
     * Get top managers by number of assigned contracts.
     *
     * @param pageable pagination info (limit to top 5)
     * @return list of top managers with contract counts
     */
    @Query("""
        SELECT new com.donatodev.bcm_backend.dto.TopManagerDTO(
            m.id,
            CONCAT(m.firstName, ' ', m.lastName),
            COUNT(c.id)
        )
        FROM Contracts c
        JOIN c.manager m
        WHERE m IS NOT NULL
        GROUP BY m.id, m.firstName, m.lastName
        ORDER BY COUNT(c.id) DESC
    """)
    List<TopManagerDTO> findTopManagers(Pageable pageable);

}
