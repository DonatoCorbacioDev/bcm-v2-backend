/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The password again, and the 2FA or recovery code when the account uses 2FA
 * (ReauthenticationService).
 *
 * @param password the signed-in user's current password
 * @param code     a 6-digit TOTP code or a recovery code; omitted without 2FA
 */
public record ReauthRequest(
        @NotBlank(message = "Password obbligatoria")
        @Size(max = 200, message = "Password troppo lunga")
        String password,
        @Pattern(regexp = "^[A-Za-z0-9-]{0,32}$", message = "Codice non valido")
        String code) {
}
