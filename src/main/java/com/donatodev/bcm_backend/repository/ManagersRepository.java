package com.donatodev.bcm_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.Managers;

/**
 * Repository interface for accessing {@link Managers} entities.
 * <p>
 * Extends {@link JpaRepository} to provide CRUD operations and custom queries.
 */
@Repository
public interface ManagersRepository extends JpaRepository<Managers, Long> {

    /**
     * Finds a {@link Managers} entity by its email.
     *
     * @param email the email of the manager
     * @return the matching manager entity, or {@code null} if not found
     */
    Managers findByEmail(String email);
    
    Page<Managers> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String first, String last, String email, Pageable pageable
        );
}
