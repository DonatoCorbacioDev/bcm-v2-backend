package com.donatodev.bcm_backend.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO used for managing users in the REST API.
 * <p>
 * Represents user credentials and role association.
 *
 * @param id the unique identifier of the user
 * @param username the username used for login (must be between 4 and 30
 * characters)
 * @param password the user's raw password (must be at least 6 characters)
 * @param managerId the ID of the associated manager (nullable)
 * @param roleId the ID of the assigned role (not null)
 * @param verified whether the user has verified their email
 */
public record UserDTO(
        Long id,
        @NotBlank(message = "Username is required")
        @Size(min = 4, max = 30, message = "Username must be between 4 and 30 characters")
        String username,
        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,
        Long managerId,
        @NotNull(message = "Role ID is required")
        Long roleId,
        Boolean verified,
        LocalDateTime createdAt
        ) {

}
