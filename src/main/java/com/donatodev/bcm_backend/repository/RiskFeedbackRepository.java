package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.RiskFeedback;

/**
 * Repository interface for accessing {@link RiskFeedback} entities.
 */
@Repository
public interface RiskFeedbackRepository extends JpaRepository<RiskFeedback, Long> {

    /**
     * Retrieves all feedback entries for the given organization, most recent first.
     * Ordered by id as a tiebreaker since two submissions can land in the same
     * clock tick and share an identical {@code createdAt}.
     *
     * @param organizationId the organization ID
     * @return a list of {@link RiskFeedback} records
     */
    List<RiskFeedback> findByOrganizationIdOrderByCreatedAtDescIdDesc(Long organizationId);

    /**
     * Retrieves all feedback entries for contracts assigned to the given manager,
     * most recent first (see {@link #findByOrganizationIdOrderByCreatedAtDescIdDesc}
     * for why {@code id} is used as a tiebreaker).
     *
     * @param managerId the manager ID
     * @return a list of {@link RiskFeedback} records
     */
    List<RiskFeedback> findByContractManagerIdOrderByCreatedAtDescIdDesc(Long managerId);
}
