/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body to explicitly set/replace the IBAN a counterparty is trusted
 * to be paid at. A separate, deliberate action from the general counterparty
 * update endpoint -- see {@code CounterpartyService#confirmVerifiedIban}.
 *
 * @param iban the new verified IBAN (required, validated against ISO 7064)
 * @param bic  the new verified BIC, optional
 */
public record VerifyCounterpartyIbanRequest(
        @NotBlank(message = "IBAN obbligatorio") String iban,
        String bic
) {}
