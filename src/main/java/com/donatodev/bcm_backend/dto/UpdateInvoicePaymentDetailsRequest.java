/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/**
 * {@code supplierIban}/{@code supplierBic} are optional here, unlike at
 * invoice upload time: the API now returns these fields masked (see
 * {@code IbanValidator#mask}), so the edit form can no longer pre-fill an
 * input with the real value — {@code null} means "leave the stored value
 * unchanged", matching {@code UpdateOrganizationRequest}'s convention, not
 * "clear it". Validated instead in {@code ElectronicInvoiceService} when
 * non-null.
 */
public record UpdateInvoicePaymentDetailsRequest(
        @Size(max = 34, message = "L'IBAN non può superare 34 caratteri")
        String supplierIban,

        @Size(max = 11, message = "Il BIC non può superare 11 caratteri")
        String supplierBic,

        LocalDate paymentDueDate
) {}
