/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.Size;
import com.donatodev.bcm_backend.validation.InputLimits;

public record RejectContractRequest(
        @Size(max = InputLimits.COMMENT, message = "Il commento non può superare 2000 caratteri") String comment) {
}
