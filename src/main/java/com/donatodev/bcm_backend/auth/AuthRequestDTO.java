package com.donatodev.bcm_backend.auth;

/**
 * Data Transfer Object (DTO) for authentication requests.
 * <p>
 * Used to encapsulate the user's login credentials.
 *
 * @param username the user's email or username
 * @param password the user's password
 */
public record AuthRequestDTO(
        String username,
        String password
) {}
