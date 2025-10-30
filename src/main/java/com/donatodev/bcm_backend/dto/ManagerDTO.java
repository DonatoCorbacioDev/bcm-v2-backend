package com.donatodev.bcm_backend.dto;

/**
 * DTO used for exposing manager data through REST APIs.
 * <p>
 * Represents a business manager with personal and departmental details.
 *
 * @param id          the unique identifier of the manager
 * @param firstName   the manager's first name
 * @param lastName    the manager's last name
 * @param email       the manager's email address
 * @param phoneNumber the manager's phone number
 * @param department  the department where the manager operates
 */
public record ManagerDTO(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String department
) {}
