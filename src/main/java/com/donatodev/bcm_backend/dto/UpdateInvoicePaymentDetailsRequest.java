/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateInvoicePaymentDetailsRequest(
        @NotBlank(message = "IBAN obbligatorio")
        @Size(max = 34, message = "L'IBAN non può superare 34 caratteri")
        String supplierIban,

        @Size(max = 11, message = "Il BIC non può superare 11 caratteri")
        String supplierBic,

        LocalDate paymentDueDate
) {}
