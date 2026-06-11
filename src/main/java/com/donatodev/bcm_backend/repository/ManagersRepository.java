package com.donatodev.bcm_backend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.Managers;

@Repository
public interface ManagersRepository extends JpaRepository<Managers, Long> {

    Managers findByEmail(String email);

    boolean existsByEmail(String email);

    Page<Managers> findByOrganizationId(Long organizationId, Pageable pageable);

    /**
     * Finds a manager by ID, scoped to the given organization. Used to
     * prevent cross-tenant access to managers by ID.
     *
     * @param id the manager ID
     * @param orgId the organization ID
     * @return an {@link Optional} containing the manager if it exists and
     * belongs to the given organization
     */
    Optional<Managers> findByIdAndOrganizationId(Long id, Long orgId);

    Page<Managers> findByOrganizationIdAndFirstNameContainingIgnoreCaseOrOrganizationIdAndLastNameContainingIgnoreCaseOrOrganizationIdAndEmailContainingIgnoreCase(
            Long orgId1, String first,
            Long orgId2, String last,
            Long orgId3, String email,
            Pageable pageable);

    Page<Managers> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String first, String last, String email, Pageable pageable);
}
