package com.donatodev.bcm_backend.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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
     * Finds all contracts belonging to the given organization. Used to scope
     * ADMIN-wide contract listings to the authenticated tenant.
     *
     * @param orgId the organization ID
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByOrganization_Id(Long orgId);

    /**
     * Finds all contracts with the specified status belonging to the given
     * organization.
     *
     * @param status the contract status to filter by
     * @param orgId the organization ID
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByStatusAndOrganization_Id(ContractStatus status, Long orgId);

    /**
     * Finds all contracts assigned to a specific manager.
     *
     * @param managerId the ID of the manager
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByManagerId(Long managerId);

    /**
     * Finds a contract by ID, scoped to the given organization. Used to
     * prevent cross-tenant access to contracts by ID.
     *
     * @param id the contract ID
     * @param orgId the organization ID
     * @return an {@link Optional} containing the contract if it exists and
     * belongs to the given organization
     */
    Optional<Contracts> findByIdAndOrganization_Id(Long id, Long orgId);

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

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.organization.id = :orgId")
    int countAllContractsByOrg(@Param("orgId") Long orgId);

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE")
    int countActiveContracts();

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE AND c.organization.id = :orgId")
    int countActiveContractsByOrg(@Param("orgId") Long orgId);

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.EXPIRED")
    int countExpiredContracts();

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.EXPIRED AND c.organization.id = :orgId")
    int countExpiredContractsByOrg(@Param("orgId") Long orgId);

    @Query("""
          SELECT COUNT(c) FROM Contracts c
          WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE
            AND c.endDate BETWEEN CURRENT_DATE AND :endDate
        """)
    int countExpiringContracts(@Param("endDate") LocalDate endDate);

    @Query("""
          SELECT COUNT(c) FROM Contracts c
          WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE
            AND c.endDate BETWEEN CURRENT_DATE AND :endDate
            AND c.organization.id = :orgId
        """)
    int countExpiringContractsByOrg(@Param("endDate") LocalDate endDate, @Param("orgId") Long orgId);

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

    @Query("""
        SELECT c FROM Contracts c
        WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE
          AND c.endDate BETWEEN :today AND :futureDate
          AND c.organization.id = :orgId
        ORDER BY c.endDate ASC
      """)
    List<Contracts> findExpiringContractsByOrg(
            @Param("today") LocalDate today,
            @Param("futureDate") LocalDate futureDate,
            @Param("orgId") Long orgId);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findAllBy(Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByStatus(ContractStatus status, Pageable pageable);

    /**
     * Paged listing of contracts belonging to the given organization. Used to
     * scope the ADMIN "no filter" search to the authenticated tenant.
     */
    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByOrganization_Id(Long orgId, Pageable pageable);

    /**
     * Paged listing of contracts with the given status belonging to the given
     * organization. Used to scope the ADMIN "status only" search to the
     * authenticated tenant.
     */
    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByStatusAndOrganization_Id(ContractStatus status, Long orgId, Pageable pageable);

    /**
     * Paged search by contract number or customer name, scoped to the given
     * organization. Used by the ADMIN "term only" search.
     */
    @EntityGraph("contracts.withManagerAndArea")
    @Query("""
        SELECT c FROM Contracts c
        WHERE c.organization.id = :orgId
          AND (LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(c.customerName) LIKE LOWER(CONCAT('%', :term, '%')))
        """)
    Page<Contracts> findByOrgAndTerm(@Param("orgId") Long orgId, @Param("term") String term, Pageable pageable);

    /**
     * Paged search by status and (contract number or customer name), scoped
     * to the given organization. Used by the ADMIN "status and term" search.
     */
    @EntityGraph("contracts.withManagerAndArea")
    @Query("""
        SELECT c FROM Contracts c
        WHERE c.organization.id = :orgId
          AND c.status = :status
          AND (LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(c.customerName) LIKE LOWER(CONCAT('%', :term, '%')))
        """)
    Page<Contracts> findByOrgAndStatusAndTerm(
            @Param("orgId") Long orgId,
            @Param("status") ContractStatus status,
            @Param("term") String term,
            Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByContractNumberContainingIgnoreCaseOrCustomerNameContainingIgnoreCase(
            String contractNumber, String customerName, Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByStatusAndContractNumberContainingIgnoreCaseOrStatusAndCustomerNameContainingIgnoreCase(
            ContractStatus s1, String contractNumber,
            ContractStatus s2, String customerName,
            Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByManagerId(Long managerId, Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByManagerIdAndStatus(Long managerId, ContractStatus status, Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByManagerIdAndContractNumberContainingIgnoreCaseOrManagerIdAndCustomerNameContainingIgnoreCase(
            Long managerId1, String contractNumber,
            Long managerId2, String customerName,
            Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
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

    @Query("""
        SELECT new com.donatodev.bcm_backend.dto.ContractsByAreaDTO(
            ba.name,
            COUNT(c.id)
        )
        FROM Contracts c
        JOIN c.businessArea ba
        WHERE c.organization.id = :orgId
        GROUP BY ba.id, ba.name
        ORDER BY COUNT(c.id) DESC
    """)
    List<ContractsByAreaDTO> countContractsByAreaAndOrg(@Param("orgId") Long orgId);

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

    @Query("""
    SELECT
        EXTRACT(YEAR FROM c.createdAt),
        EXTRACT(MONTH FROM c.createdAt),
        COUNT(c)
    FROM Contracts c
    WHERE c.createdAt >= :sixMonthsAgo
      AND c.organization.id = :orgId
    GROUP BY EXTRACT(YEAR FROM c.createdAt), EXTRACT(MONTH FROM c.createdAt)
    ORDER BY EXTRACT(YEAR FROM c.createdAt), EXTRACT(MONTH FROM c.createdAt)
""")
    List<Object[]> countContractsByMonthAndOrg(
            @Param("sixMonthsAgo") LocalDateTime sixMonthsAgo,
            @Param("orgId") Long orgId);

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

    @Query("""
        SELECT new com.donatodev.bcm_backend.dto.TopManagerDTO(
            m.id,
            CONCAT(m.firstName, ' ', m.lastName),
            COUNT(c.id)
        )
        FROM Contracts c
        JOIN c.manager m
        WHERE m IS NOT NULL
          AND c.organization.id = :orgId
        GROUP BY m.id, m.firstName, m.lastName
        ORDER BY COUNT(c.id) DESC
    """)
    List<TopManagerDTO> findTopManagersByOrg(Pageable pageable, @Param("orgId") Long orgId);

    /**
     * Find contracts by status and end date within a date range.
     *
     * @param status the contract status
     * @param startDate the start date of the range
     * @param endDate the end date of the range
     * @return list of contracts matching the criteria
     */
    List<Contracts> findByStatusAndEndDateBetween(
            ContractStatus status,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Finds all contracts with the given status whose end date is before the specified date.
     * Used by the scheduler to expire overdue contracts without loading all active contracts.
     *
     * @param status  the contract status to filter by
     * @param endDate the cutoff date (contracts ending before this date are returned)
     * @return list of matching contracts
     */
    List<Contracts> findByStatusAndEndDateBefore(ContractStatus status, LocalDate endDate);

    /**
     * Finds all contracts with the given status whose end date falls exactly on the specified date.
     * Used by the scheduler to send threshold-based expiration notifications (30/14/7/1 days).
     *
     * @param status  the contract status to filter by
     * @param endDate the exact end date to match
     * @return list of matching contracts
     */
    List<Contracts> findByStatusAndEndDate(ContractStatus status, LocalDate endDate);

    @Query("""
            SELECT COUNT(c) FROM Contracts c
            WHERE c.organization.id = :orgId
              AND EXTRACT(YEAR FROM c.createdAt) = :year
              AND EXTRACT(MONTH FROM c.createdAt) = :month
            """)
    long countNewContractsByOrgAndYearMonth(
            @Param("orgId") Long orgId,
            @Param("year") int year,
            @Param("month") int month);
}
