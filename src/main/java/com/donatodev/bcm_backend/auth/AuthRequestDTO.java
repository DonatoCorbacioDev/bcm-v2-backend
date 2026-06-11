package com.donatodev.bcm_backend.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object (DTO) for authentication requests.
 * <p>
 * Used to encapsulate the user's login credentials.
 *
 * @param username the user's email or username
 * @param password the user's password
 * @param organizationSlug optional slug identifying the organization the user belongs to,
 *                          used to disambiguate usernames shared across organizations
 */
public record AuthRequestDTO(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required") String password,
        String organizationSlug
) {
    public AuthRequestDTO(String username, String password) {
        this(username, password, null);
    }
}
