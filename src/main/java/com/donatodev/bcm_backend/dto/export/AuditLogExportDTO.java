/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.Instant;

import com.donatodev.bcm_backend.entity.AuditLog;

/** One element of {@code data/audit_logs.json}. */
public record AuditLogExportDTO(
        Long id,
        String action,
        String entityType,
        Long entityId,
        String username,
        Instant timestamp,
        String details) {

    public static AuditLogExportDTO from(AuditLog log) {
        return new AuditLogExportDTO(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getUsername(),
                log.getTimestamp(),
                log.getDetails());
    }
}
