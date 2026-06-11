package com.donatodev.bcm_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.FinancialTypes;

@Repository
public interface FinancialTypesRepository extends JpaRepository<FinancialTypes, Long> {

    FinancialTypes findByName(String name);

    List<FinancialTypes> findAllByOrganizationId(Long organizationId);

    /**
     * Finds a financial type by ID, scoped to the given organization. Used to
     * prevent cross-tenant access to financial types by ID.
     *
     * @param id the financial type ID
     * @param organizationId the organization ID
     * @return an {@link Optional} containing the financial type if it exists and
     * belongs to the given organization
     */
    Optional<FinancialTypes> findByIdAndOrganizationId(Long id, Long organizationId);
}
