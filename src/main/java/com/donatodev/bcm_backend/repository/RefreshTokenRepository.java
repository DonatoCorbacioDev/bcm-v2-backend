/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.RefreshToken;
import com.donatodev.bcm_backend.entity.Users;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    /**
     * Revokes one token at its rotation, only if it is still active, in one
     * statement: of two refreshes with the same token at the same moment only
     * one gets 1 back, so a token is never rotated twice into two live
     * sessions. Revoked means rotated away and nothing else: such a token
     * presented again is a stolen one.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.id = :id AND r.revoked = false")
    int revokeIfActive(@Param("id") UUID id);

    /** Rotated tokens, kept so that a replay is still recognised, are dropped after a while. */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiryDate < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    /**
     * Ends the account's live sessions (logout, a new login, a credential
     * change, a replay): their tokens are deleted, so a device still holding
     * one just gets a 401. The rotated ones stay, to recognise a replay.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.user = :user AND r.revoked = false")
    void deleteActiveByUser(@Param("user") Users user);
}
