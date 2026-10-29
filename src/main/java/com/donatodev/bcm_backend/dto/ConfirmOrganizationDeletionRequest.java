/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** @param code the 6-digit code emailed to the admin who asked for the deletion */
public record ConfirmOrganizationDeletionRequest(

        @NotBlank(message = "Codice di conferma obbligatorio")
        @Pattern(regexp = "\\d{6}", message = "Il codice di conferma è composto da 6 cifre")
        String code
) {}
