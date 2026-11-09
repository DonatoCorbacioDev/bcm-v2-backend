/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record CreateSepaPaymentRequest(
        @NotEmpty(message = "Selezionare almeno una fattura")
        List<UUID> invoiceIds,

        LocalDate executionDate
) {}
