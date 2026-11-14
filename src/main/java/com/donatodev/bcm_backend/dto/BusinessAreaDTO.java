/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.donatodev.bcm_backend.validation.InputLimits;

/**
 * Data Transfer Object for Business Area.
 * <p>
 * Used to transfer data between client and server in REST API operations.
 *
 * @param id          the ID of the business area
 * @param name        the name of the business area
 * @param description a brief description of the business area
 */
public record BusinessAreaDTO(
        UUID id,
        @NotBlank(message = "Nome obbligatorio")
        @Size(max = 255, message = "Il nome non può superare 255 caratteri") String name,
        @Size(max = InputLimits.LONG_TEXT, message = "La descrizione è troppo lunga") String description
) {}
