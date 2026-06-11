package com.donatodev.bcm_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.BusinessAreas;

@Repository
public interface BusinessAreasRepository extends JpaRepository<BusinessAreas, Long> {

    BusinessAreas findByName(String name);

    List<BusinessAreas> findAllByOrganizationId(Long organizationId);

    /**
     * Finds a business area by ID, scoped to the given organization. Used to
     * prevent cross-tenant access to business areas by ID.
     *
     * @param id the business area ID
     * @param organizationId the organization ID
     * @return an {@link Optional} containing the business area if it exists and
     * belongs to the given organization
     */
    Optional<BusinessAreas> findByIdAndOrganizationId(Long id, Long organizationId);
}
