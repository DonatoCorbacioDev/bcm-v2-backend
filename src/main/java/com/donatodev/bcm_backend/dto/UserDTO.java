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
 * @param canApproveContracts whether the user can approve/reject contracts submitted for review
 */
public record UserDTO(
        Long id,
        @NotBlank(message = "Username obbligatorio")
        @Size(min = 4, max = 30, message = "Lo username deve avere tra 4 e 30 caratteri")
        String username,
        @NotBlank(message = "Password obbligatoria")
        @Size(min = 6, message = "La password deve contenere almeno 6 caratteri")
        String password,
        Long managerId,
        @NotNull(message = "ID ruolo obbligatorio")
        Long roleId,
        Boolean verified,
        LocalDateTime createdAt,
        Boolean canApproveContracts
        ) {

    /**
     * Compatibility constructor for call sites predating the approval
     * workflow permission — defaults {@code canApproveContracts} to false.
     */
    public UserDTO(Long id, String username, String password, Long managerId, Long roleId,
            Boolean verified, LocalDateTime createdAt) {
        this(id, username, password, managerId, roleId, verified, createdAt, false);
    }
}
