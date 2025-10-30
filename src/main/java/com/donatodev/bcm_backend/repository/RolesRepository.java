package com.donatodev.bcm_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.Roles;

/**
 * Repository interface for accessing {@link Roles} entities.
 * <p>
 * Extends {@link JpaRepository} to provide CRUD operations and custom queries.
 */
@Repository
public interface RolesRepository extends JpaRepository<Roles, Long> {

    /**
     * Finds a {@link Roles} entity by its role name.
     *
     * @param role the name of the role
     * @return an {@link Optional} containing the matching role if found, or empty otherwise
     */
    Optional<Roles> findByRole(String role);
}
