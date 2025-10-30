package com.donatodev.bcm_backend.auth;

/**
 * Data Transfer Object (DTO) for authentication responses.
 * <p>
 * Contains the JWT token returned after successful login.
 *
 * @param token the JWT token used for authenticated requests
 */
public record AuthResponseDTO(
        String token
) {}
