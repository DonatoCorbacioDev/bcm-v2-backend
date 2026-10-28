/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.donatodev.bcm_backend.entity.PendingRegistration;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    Optional<PendingRegistration> findByPublicId(String publicId);

    /** Drops earlier attempts for the same address, so only the latest code is valid. */
    @Modifying
    @Query("DELETE FROM PendingRegistration p WHERE p.adminEmail = :email")
    int deleteByAdminEmail(@Param("email") String email);

    @Modifying
    @Query("DELETE FROM PendingRegistration p WHERE p.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
