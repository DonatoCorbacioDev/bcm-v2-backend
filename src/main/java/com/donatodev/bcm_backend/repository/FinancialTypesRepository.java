package com.donatodev.bcm_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.FinancialTypes;

/**
 * Repository interface for accessing {@link FinancialTypes} entities.
 * <p>
 * Provides CRUD operations and a method to retrieve a financial type by name.
 */
@Repository
public interface FinancialTypesRepository extends JpaRepository<FinancialTypes, Long> {

    /**
     * Finds a {@link FinancialTypes} entity by its name.
     *
     * @param name the name of the financial type
     * @return the matching financial type entity, or {@code null} if not found
     */
    FinancialTypes findByName(String name);
}
