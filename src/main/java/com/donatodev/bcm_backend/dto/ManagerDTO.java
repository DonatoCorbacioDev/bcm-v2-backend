/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.donatodev.bcm_backend.validation.OnCreate;

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
        UUID id,
        @NotBlank(groups = OnCreate.class, message = "Nome obbligatorio")
        @Size(max = 50, message = "Il nome non può superare 50 caratteri") String firstName,
        @NotBlank(groups = OnCreate.class, message = "Cognome obbligatorio")
        @Size(max = 50, message = "Il cognome non può superare 50 caratteri") String lastName,
        @NotBlank(groups = OnCreate.class, message = "Email obbligatoria")
        @Email(message = "Email non valida")
        @Size(max = 100, message = "L'email non può superare 100 caratteri") String email,
        @Size(max = 20, message = "Il telefono non può superare 20 caratteri") String phoneNumber,
        @Size(max = 50, message = "Il reparto non può superare 50 caratteri") String department
) {}
