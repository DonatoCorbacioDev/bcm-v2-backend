/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.donatodev.bcm_backend.entity.PasswordResetToken;
import com.donatodev.bcm_backend.entity.Users;

/**
 * Repository interface for accessing {@link PasswordResetToken} entities.
 * <p>
 * Provides methods to find password reset tokens by token string or associated user.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Finds a {@link PasswordResetToken} entity by its token string.
     *
     * @param token the token string
     * @return an {@link Optional} containing the matching token if found, or empty otherwise
     */
    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Finds a {@link PasswordResetToken} entity associated with a specific user.
     *
     * @param user the user entity
     * @return an {@link Optional} containing the matching token if found, or empty otherwise
     */
    Optional<PasswordResetToken> findByUser(Users user);

    /**
     * Deletes one reset link, to use it: of two requests with the same link
     * at the same time, MySQL lets one delete the row and the other find
     * nothing, so a link sets a password once (OWASP Database Security,
     * concurrent updates).
     *
     * @param id the link's id
     * @return 1 if this call deleted it, 0 if it was already gone
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.id = :id")
    int deleteToUse(@Param("id") UUID id);
}
