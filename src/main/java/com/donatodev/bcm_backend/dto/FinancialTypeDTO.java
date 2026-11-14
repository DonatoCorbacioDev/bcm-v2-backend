/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.donatodev.bcm_backend.validation.InputLimits;

/**
 * Data Transfer Object for Financial Types.
 * <p>
 * Represents the category or type of financial data associated with a contract.
 *
 * @param id          the unique identifier of the financial type
 * @param name        the name of the financial type (e.g., Revenue, Cost)
 * @param description a brief description of the financial type
 * @param category    whether this type represents revenue or a cost
 */
public record FinancialTypeDTO(
        UUID id,
        @NotBlank(message = "Nome obbligatorio")
        @Size(max = 255, message = "Il nome non può superare 255 caratteri") String name,
        @Size(max = InputLimits.LONG_TEXT, message = "La descrizione è troppo lunga") String description,
        @NotNull(message = "Categoria obbligatoria") FinancialCategory category
) {}
