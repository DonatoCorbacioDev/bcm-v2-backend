/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.donatodev.bcm_backend.validation.InputLimits;

public record SemanticSearchRequestDTO(
        @NotBlank(message = "La ricerca non può essere vuota")
        @Size(max = InputLimits.QUESTION, message = "La ricerca non può superare 2000 caratteri") String query) {}
