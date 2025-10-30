package com.donatodev.bcm_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.BusinessAreas;

/**
 * Repository interface for accessing {@link BusinessAreas} entities.
 * <p>
 * Extends {@link JpaRepository} to provide CRUD operations and custom queries.
 */
@Repository
public interface BusinessAreasRepository extends JpaRepository<BusinessAreas, Long> {

    /**
     * Finds a {@link BusinessAreas} entity by its name.
     *
     * @param name the name of the business area
     * @return the matching {@link BusinessAreas} entity, or {@code null} if not found
     */
    BusinessAreas findByName(String name);
}
