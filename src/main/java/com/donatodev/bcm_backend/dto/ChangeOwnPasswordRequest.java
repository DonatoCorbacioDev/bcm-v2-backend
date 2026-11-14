/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;

import com.donatodev.bcm_backend.validation.ValidPassword;
import jakarta.validation.constraints.Size;
import com.donatodev.bcm_backend.validation.InputLimits;

/**
 * A signed-in user changing their own password from the profile page.
 *
 * @param currentPassword the password they use today, so a session left open
 *                        on someone else's screen can't be used to take over
 *                        the account
 * @param newPassword     the new password
 */
public record ChangeOwnPasswordRequest(
        @NotBlank(message = "Password attuale obbligatoria")
        @Size(max = InputLimits.PASSWORD_CHECKED, message = "Password attuale non valida")
        String currentPassword,

        @ValidPassword(message = "Nuova password obbligatoria")
        String newPassword
) {}
