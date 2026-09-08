/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.time.Instant;

public record AuditLogDTO(
        Long id,
        String action,
        String entityType,
        Long entityId,
        String username,
        Long orgId,
        Instant timestamp,
        String details
) {}
