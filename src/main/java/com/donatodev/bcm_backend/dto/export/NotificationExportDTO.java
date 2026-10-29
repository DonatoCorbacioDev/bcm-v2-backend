/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.Notification;
import com.donatodev.bcm_backend.entity.NotificationType;

/** One element of {@code data/notifications.json}. */
public record NotificationExportDTO(
        Long id,
        Long userId,
        String title,
        String message,
        NotificationType type,
        boolean read,
        LocalDateTime createdAt) {

    public static NotificationExportDTO from(Notification notification) {
        return new NotificationExportDTO(
                notification.getId(),
                notification.getUser().getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
