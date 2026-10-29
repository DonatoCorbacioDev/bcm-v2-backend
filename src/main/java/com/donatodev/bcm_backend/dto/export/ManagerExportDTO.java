/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.Managers;

/** One element of {@code data/managers.json}. */
public record ManagerExportDTO(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String department,
        LocalDateTime createdAt) {

    public static ManagerExportDTO from(Managers manager) {
        return new ManagerExportDTO(
                manager.getId(),
                manager.getFirstName(),
                manager.getLastName(),
                manager.getEmail(),
                manager.getPhoneNumber(),
                manager.getDepartment(),
                manager.getCreatedAt());
    }
}
