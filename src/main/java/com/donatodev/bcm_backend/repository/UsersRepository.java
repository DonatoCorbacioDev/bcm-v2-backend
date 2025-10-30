package com.donatodev.bcm_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.Users;

/**
 * Repository interface for accessing {@link Users} entities.
 * <p>
 * Provides CRUD operations and methods for checking existence and retrieving users by username or manager.
 */
@Repository
public interface UsersRepository extends JpaRepository<Users, Long>, JpaSpecificationExecutor<Users> {

    /**
     * Finds a {@link Users} entity by its username.
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the matching user if found, or empty otherwise
     */
    Optional<Users> findByUsername(String username);

    /**
     * Checks if any user exists with the given manager ID.
     *
     * @param managerId the manager ID to check
     * @return {@code true} if at least one user exists with the given manager ID, {@code false} otherwise
     */
    boolean existsByManagerId(Long managerId);

    /**
     * Checks if a user exists with the given username.
     *
     * @param username the username to check
     * @return {@code true} if a user exists with the given username, {@code false} otherwise
     */
    boolean existsByUsername(String username);
}
