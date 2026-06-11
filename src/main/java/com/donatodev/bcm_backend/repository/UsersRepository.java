package com.donatodev.bcm_backend.repository;

import java.util.List;
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

    /**
     * Finds a user whose associated manager has the given email (case-insensitive).
     *
     * @param email the manager's email address
     * @return an {@link Optional} containing the matching user, or empty if none found
     */
    Optional<Users> findByManagerEmailIgnoreCase(String email);

    /**
     * Finds the first user with the given role name.
     * Used by the scheduler to attribute system-generated history records to an admin account.
     *
     * @param roleName the role name (e.g., "ADMIN")
     * @return an {@link Optional} containing the first matching user, or empty if none found
     */
    Optional<Users> findFirstByRoleRole(String roleName);

    List<Users> findByOrganizationIdAndRoleRole(Long orgId, String roleName);

    /**
     * Finds a user by ID, scoped to the given organization. Used to prevent
     * cross-tenant access to users by ID.
     *
     * @param id the user ID
     * @param orgId the organization ID
     * @return an {@link Optional} containing the user if it exists and
     * belongs to the given organization
     */
    Optional<Users> findByIdAndOrganizationId(Long id, Long orgId);

    /**
     * Finds all users belonging to the given organization.
     *
     * @param orgId the organization ID
     * @return a list of users belonging to the given organization
     */
    List<Users> findAllByOrganizationId(Long orgId);

    /**
     * Finds all users with the given username, across all organizations.
     * <p>
     * Since usernames are only unique per-organization (see {@code uq_usr_name_org}),
     * this can return more than one result. Used during login to detect
     * ambiguous usernames that require an {@code organizationSlug} to disambiguate.
     *
     * @param username the username to search for
     * @return the list of users with the given username, possibly empty or containing more than one entry
     */
    List<Users> findAllByUsername(String username);

    /**
     * Finds a user by username scoped to the organization identified by the given slug.
     * Used during login when an {@code organizationSlug} is provided to disambiguate
     * usernames that exist in multiple organizations.
     *
     * @param username the username to search for
     * @param organizationSlug the slug of the organization the user belongs to
     * @return an {@link Optional} containing the matching user, or empty if none found
     */
    Optional<Users> findByUsernameAndOrganizationSlug(String username, String organizationSlug);

    /**
     * Finds a user by username scoped to the given organization ID. Used by the
     * per-request JWT authentication path, since usernames are only unique per-organization.
     *
     * @param username the username to search for
     * @param organizationId the ID of the organization the user belongs to
     * @return an {@link Optional} containing the matching user, or empty if none found
     */
    Optional<Users> findByUsernameAndOrganizationId(String username, Long organizationId);
}
