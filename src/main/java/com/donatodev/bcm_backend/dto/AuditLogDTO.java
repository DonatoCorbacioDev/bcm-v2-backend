/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.time.Instant;

public record AuditLogDTO(
        UUID id,
        String action,
        String entityType,
        UUID entityId,
        String username,
        UUID orgId,
        Instant timestamp,
        String details
) {}
