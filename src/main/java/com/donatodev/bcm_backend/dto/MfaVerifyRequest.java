/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequest(
        @NotBlank(message = "Token MFA obbligatorio")
        String mfaToken,

        @NotBlank(message = "Codice obbligatorio")
        String code
) {}
