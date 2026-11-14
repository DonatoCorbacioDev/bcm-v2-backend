/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateSepaPaymentRequest(
        @NotEmpty(message = "Selezionare almeno una fattura")
        @Size(max = 500, message = "Al massimo 500 fatture per pagamento")
        List<UUID> invoiceIds,

        LocalDate executionDate
) {}
