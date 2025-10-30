package com.donatodev.bcm_backend.dto;

/**
 * DTO used to manage user roles in the REST API.
 * <p>
 * Represents a role assigned to a user (e.g., ADMIN, MANAGER).
 *
 * @param id    the unique identifier of the role
 * @param role  the name of the role (e.g., "ADMIN", "MANAGER")
 */
public record RoleDTO(
        Long id,
        String role
) {}
