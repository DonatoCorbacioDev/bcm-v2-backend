/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.time.LocalDate;
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
public interface ContractsRepository extends JpaRepository<Contracts, UUID> {

    /**
     * Finds all contracts with the specified status.
     *
     * @param status the contract status to filter by
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByStatus(ContractStatus status);

    /**
     * Checks whether a contract with the given contract number already
     * exists in the specified organization. Used by bulk import to reject duplicate rows before hitting
     * the database's unique constraint.
     *
     * @param contractNumber the contract number to check
     * @param organizationId the organization to search
     * @return {@code true} if this organization already has a contract with this number
     */
    boolean existsByContractNumberAndOrganizationId(String contractNumber, UUID organizationId);

    /**
     * Finds all contracts belonging to the given organization. Used to scope
     * ADMIN-wide contract listings to the authenticated tenant.
     *
     * @param orgId the organization ID
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByOrganization_Id(UUID orgId);

    /**
     * Finds all contracts with the specified status belonging to the given
     * organization.
     *
     * @param status the contract status to filter by
     * @param orgId the organization ID
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByStatusAndOrganization_Id(ContractStatus status, UUID orgId);

    /**
     * Finds all contracts assigned to a specific manager.
     *
     * @param managerId the ID of the manager
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByManagerId(UUID managerId);

    /**
     * Finds a contract by ID, scoped to the given organization. Used to
     * prevent cross-tenant access to contracts by ID.
     *
     * @param id the contract ID
     * @param orgId the organization ID
     * @return an {@link Optional} containing the contract if it exists and
     * belongs to the given organization
     */
    Optional<Contracts> findByIdAndOrganization_Id(UUID id, UUID orgId);

    /** Active contracts renewing tacitly with a stated notice period: reminded about their notice deadline. */
    List<Contracts> findByStatusAndAutoRenewTrueAndNoticeDaysIsNotNull(ContractStatus status);

    /**
     * Finds all contracts assigned to a specific manager and having the
     * specified status.
     *
     * @param managerId the ID of the manager
     * @param status the contract status to filter by
     * @return a list of matching {@link Contracts}
     */
    List<Contracts> findByManagerIdAndStatus(UUID managerId, ContractStatus status);

    @Query("SELECT COUNT(c) FROM Contracts c")
    int countAllContracts();

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.organization.id = :orgId")
    int countAllContractsByOrg(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE")
    int countActiveContracts();

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE AND c.organization.id = :orgId")
    int countActiveContractsByOrg(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.EXPIRED")
    int countExpiredContracts();

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.EXPIRED AND c.organization.id = :orgId")
    int countExpiredContractsByOrg(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.DRAFT")
    int countDraftContracts();

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.DRAFT AND c.organization.id = :orgId")
    int countDraftContractsByOrg(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.manager.id = :managerId")
    int countAllContractsByManager(@Param("managerId") UUID managerId);

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE AND c.manager.id = :managerId")
    int countActiveContractsByManager(@Param("managerId") UUID managerId);

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.EXPIRED AND c.manager.id = :managerId")
    int countExpiredContractsByManager(@Param("managerId") UUID managerId);

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.DRAFT AND c.manager.id = :managerId")
    int countDraftContractsByManager(@Param("managerId") UUID managerId);

    @Query("SELECT COUNT(c) FROM Contracts c WHERE c.counterparty.id = :counterpartyId AND c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE")
    int countActiveContractsByCounterpartyId(@Param("counterpartyId") UUID counterpartyId);

    @Query("SELECT COALESCE(SUM(c.annualValue), 0.0) FROM Contracts c WHERE c.counterparty.id = :counterpartyId AND c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE")
    double sumActiveAnnualValueByCounterpartyId(@Param("counterpartyId") UUID counterpartyId);

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
    int countExpiringContractsByOrg(@Param("endDate") LocalDate endDate, @Param("orgId") UUID orgId);

    @Query("""
          SELECT COUNT(c) FROM Contracts c
          WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE
            AND c.endDate BETWEEN CURRENT_DATE AND :endDate
            AND c.manager.id = :managerId
        """)
    int countExpiringContractsByManager(@Param("endDate") LocalDate endDate, @Param("managerId") UUID managerId);

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
            @Param("orgId") UUID orgId);

    @Query("""
        SELECT c FROM Contracts c
        WHERE c.status = com.donatodev.bcm_backend.entity.ContractStatus.ACTIVE
          AND c.endDate BETWEEN :today AND :futureDate
          AND c.manager.id = :managerId
        ORDER BY c.endDate ASC
      """)
    List<Contracts> findExpiringContractsByManager(
            @Param("today") LocalDate today,
            @Param("futureDate") LocalDate futureDate,
            @Param("managerId") UUID managerId);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findAllBy(Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByStatus(ContractStatus status, Pageable pageable);

    /**
     * Paged listing of contracts belonging to the given organization. Used to
     * scope the ADMIN "no filter" search to the authenticated tenant.
     */
    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByOrganization_Id(UUID orgId, Pageable pageable);

    /**
     * Paged listing of contracts with the given status belonging to the given
     * organization. Used to scope the ADMIN "status only" search to the
     * authenticated tenant.
     */
    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByStatusAndOrganization_Id(ContractStatus status, UUID orgId, Pageable pageable);

    /**
     * Paged search by contract number or counterparty name, scoped to the
     * given organization. Used by the ADMIN "term only" search.
     */
    @EntityGraph("contracts.withManagerAndArea")
    @Query("""
        SELECT c FROM Contracts c
        WHERE c.organization.id = :orgId
          AND (LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(c.counterparty.name) LIKE LOWER(CONCAT('%', :term, '%')))
        """)
    Page<Contracts> findByOrgAndTerm(@Param("orgId") UUID orgId, @Param("term") String term, Pageable pageable);

    /**
     * Paged search by status and (contract number or counterparty name),
     * scoped to the given organization. Used by the ADMIN "status and term" search.
     */
    @EntityGraph("contracts.withManagerAndArea")
    @Query("""
        SELECT c FROM Contracts c
        WHERE c.organization.id = :orgId
          AND c.status = :status
          AND (LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(c.counterparty.name) LIKE LOWER(CONCAT('%', :term, '%')))
        """)
    Page<Contracts> findByOrgAndStatusAndTerm(
            @Param("orgId") UUID orgId,
            @Param("status") ContractStatus status,
            @Param("term") String term,
            Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByContractNumberContainingIgnoreCaseOrCounterpartyNameContainingIgnoreCase(
            String contractNumber, String counterpartyName, Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByStatusAndContractNumberContainingIgnoreCaseOrStatusAndCounterpartyNameContainingIgnoreCase(
            ContractStatus s1, String contractNumber,
            ContractStatus s2, String counterpartyName,
            Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByManagerId(UUID managerId, Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByManagerIdAndStatus(UUID managerId, ContractStatus status, Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByManagerIdAndContractNumberContainingIgnoreCaseOrManagerIdAndCounterpartyNameContainingIgnoreCase(
            UUID managerId1, String contractNumber,
            UUID managerId2, String counterpartyName,
            Pageable pageable);

    @EntityGraph("contracts.withManagerAndArea")
    Page<Contracts> findByManagerIdAndStatusAndContractNumberContainingIgnoreCaseOrManagerIdAndStatusAndCounterpartyNameContainingIgnoreCase(
            UUID managerId1, ContractStatus status1, String contractNumber,
            UUID managerId2, ContractStatus status2, String counterpartyName,
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
    List<ContractsByAreaDTO> countContractsByAreaAndOrg(@Param("orgId") UUID orgId);

    @Query("""
        SELECT new com.donatodev.bcm_backend.dto.ContractsByAreaDTO(
            ba.name,
            COUNT(c.id)
        )
        FROM Contracts c
        JOIN c.businessArea ba
        WHERE c.manager.id = :managerId
        GROUP BY ba.id, ba.name
        ORDER BY COUNT(c.id) DESC
    """)
    List<ContractsByAreaDTO> countContractsByAreaAndManager(@Param("managerId") UUID managerId);

    /**
     * Count contracts started (by start_date) per month from a given window
     * start onward. Uses HQL with EXTRACT functions for cross-database
     * compatibility. The caller zero-fills months with no matches.
     *
     * @param windowStart the first day of the earliest month to include
     * @return list of Object arrays containing [year, month, count]
     */
    @Query("""
    SELECT
        EXTRACT(YEAR FROM c.startDate),
        EXTRACT(MONTH FROM c.startDate),
        COUNT(c)
    FROM Contracts c
    WHERE c.startDate >= :windowStart
    GROUP BY EXTRACT(YEAR FROM c.startDate), EXTRACT(MONTH FROM c.startDate)
    ORDER BY EXTRACT(YEAR FROM c.startDate), EXTRACT(MONTH FROM c.startDate)
""")
    List<Object[]> countContractsByMonth(@Param("windowStart") LocalDate windowStart);

    @Query("""
    SELECT
        EXTRACT(YEAR FROM c.startDate),
        EXTRACT(MONTH FROM c.startDate),
        COUNT(c)
    FROM Contracts c
    WHERE c.startDate >= :windowStart
      AND c.organization.id = :orgId
    GROUP BY EXTRACT(YEAR FROM c.startDate), EXTRACT(MONTH FROM c.startDate)
    ORDER BY EXTRACT(YEAR FROM c.startDate), EXTRACT(MONTH FROM c.startDate)
""")
    List<Object[]> countContractsByMonthAndOrg(
            @Param("windowStart") LocalDate windowStart,
            @Param("orgId") UUID orgId);

    @Query("""
    SELECT
        EXTRACT(YEAR FROM c.startDate),
        EXTRACT(MONTH FROM c.startDate),
        COUNT(c)
    FROM Contracts c
    WHERE c.startDate >= :windowStart
      AND c.manager.id = :managerId
    GROUP BY EXTRACT(YEAR FROM c.startDate), EXTRACT(MONTH FROM c.startDate)
    ORDER BY EXTRACT(YEAR FROM c.startDate), EXTRACT(MONTH FROM c.startDate)
""")
    List<Object[]> countContractsByMonthAndManager(
            @Param("windowStart") LocalDate windowStart,
            @Param("managerId") UUID managerId);

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
    List<TopManagerDTO> findTopManagersByOrg(Pageable pageable, @Param("orgId") UUID orgId);

    /**
     * Get the calling manager's own contract count, in the same shape as
     * {@link #findTopManagers}/{@link #findTopManagersByOrg}. A MANAGER only
     * sees their own contracts, so "top managers" collapses to a single
     * (possibly empty) row instead of ranking colleagues.
     */
    @Query("""
        SELECT new com.donatodev.bcm_backend.dto.TopManagerDTO(
            m.id,
            CONCAT(m.firstName, ' ', m.lastName),
            COUNT(c.id)
        )
        FROM Contracts c
        JOIN c.manager m
        WHERE m.id = :managerId
        GROUP BY m.id, m.firstName, m.lastName
    """)
    List<TopManagerDTO> findTopManagerForManager(@Param("managerId") UUID managerId);

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
            @Param("orgId") UUID orgId,
            @Param("year") int year,
            @Param("month") int month);
}
