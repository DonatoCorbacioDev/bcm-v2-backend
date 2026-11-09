/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndUserIdAndOrgId(UUID id, UUID userId, UUID orgId);

    List<Notification> findByUserIdAndOrgIdOrderByCreatedAtDesc(UUID userId, UUID orgId);

    long countByUserIdAndOrgIdAndReadFalse(UUID userId, UUID orgId);

    List<Notification> findAllByOrgId(UUID orgId);
}
