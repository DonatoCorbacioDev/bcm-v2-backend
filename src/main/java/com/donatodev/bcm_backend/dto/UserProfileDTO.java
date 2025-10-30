package com.donatodev.bcm_backend.dto;

/**
 * DTO used for returning public profile information of a user.
 * <p>
 * This is typically used in GET endpoints to expose only safe and relevant
 * information about the currently authenticated user.
 *
 * @param id       the unique identifier of the user
 * @param username the username of the user
 * @param role     the role assigned to the user (e.g., ADMIN, MANAGER)
 * @param verified true if the user's email has been verified
 */
public record UserProfileDTO(
    Long id,
    String username,
    String role,
    boolean verified
) {}
