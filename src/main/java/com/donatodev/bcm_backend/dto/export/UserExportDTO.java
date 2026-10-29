/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.Users;

/**
 * One element of {@code data/users.json}. Left out, because each would let
 * whoever holds the file into the account or past its protections:
 * passwordHash, totpSecretEncrypted, calendarToken (the secret in the .ics
 * feed URL), and the lockout state (failedLoginAttempts, lockedUntil).
 */
public record UserExportDTO(
        Long id,
        String username,
        String role,
        Long managerId,
        boolean verified,
        boolean totpEnabled,
        boolean canApproveContracts,
        LocalDateTime createdAt) {

    public static UserExportDTO from(Users user) {
        return new UserExportDTO(
                user.getId(),
                user.getUsername(),
                user.getRole() != null ? user.getRole().getRole() : null,
                // An invited user may not have completed the invite yet.
                user.getManager() != null ? user.getManager().getId() : null,
                user.isVerified(),
                user.isTotpEnabled(),
                user.isCanApproveContracts(),
                user.getCreatedAt());
    }
}
