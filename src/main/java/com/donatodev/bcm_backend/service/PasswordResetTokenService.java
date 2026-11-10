/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.PasswordResetToken;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.PasswordResetTokenRepository;
import com.donatodev.bcm_backend.util.TokenHash;

import lombok.RequiredArgsConstructor;

/**
 * Service class for managing password reset tokens.
 * <p>
 * Provides methods to create, retrieve, and delete password reset tokens.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private final PasswordResetTokenRepository repository;

    /**
     * Creates a new password reset token for the given user.
     * Deletes any existing token for the user before creating a new one.
     * The token expires 30 minutes after creation.
     *
     * @param user the user for whom to create the token
     * @return the created {@link PasswordResetToken}
     */
    public String createToken(Users user) {
        repository.findByUser(user).ifPresent(repository::delete);

        // Only the hash is stored (OWASP API2): the plain token exists in the
        // emailed link alone, so the database holds nothing that works as one.
        String token = UUID.randomUUID().toString();
        repository.save(PasswordResetToken.builder()
                .token(TokenHash.sha256Hex(token))
                .expiryDate(LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(30))
                .user(user)
                .build());
        return token;
    }

    /**
     * Retrieves a password reset token by its token string.
     *
     * @param token the token string to search for
     * @return the corresponding {@link PasswordResetToken}
     * @throws RuntimeException if the token is not found
     */
    public PasswordResetToken getByToken(String token) {
        return repository.findByToken(TokenHash.sha256Hex(token))
                .orElseThrow(() -> new RuntimeException("Reset token not found"));
    }

    /**
     * Deletes a given password reset token.
     *
     * @param token the {@link PasswordResetToken} to delete
     */
    public void deleteToken(PasswordResetToken token) {
        repository.delete(token);
    }
}
