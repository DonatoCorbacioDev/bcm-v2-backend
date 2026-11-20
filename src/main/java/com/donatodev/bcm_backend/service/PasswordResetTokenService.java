/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final int VALIDITY_MINUTES = 30;

    private final PasswordResetTokenRepository repository;
    private final UserService userService;

    /**
     * Whether a link was sent to this user less than {@code minutes} ago
     * (OWASP API4): told from the pending link's expiry, which is its creation
     * plus {@value #VALIDITY_MINUTES} minutes.
     */
    public boolean sentWithinMinutes(Users user, int minutes) {
        LocalDateTime createdAfter = LocalDateTime.now(ZoneId.systemDefault()).minusMinutes(minutes);
        return repository.findByUser(user)
                .map(token -> token.getExpiryDate().minusMinutes(VALIDITY_MINUTES).isAfter(createdAfter))
                .orElse(false);
    }

    /**
     * Creates a new password reset token for the given user.
     * Deletes any existing token for the user before creating a new one.
     * The token expires 30 minutes after creation.
     *
     * @param user the user for whom to create the token
     * @return the created {@link PasswordResetToken}
     */
    @Transactional
    public String createToken(Users user) {
        repository.findByUser(user).ifPresent(repository::delete);

        // Only the hash is stored (OWASP API2): the plain token exists in the
        // emailed link alone, so the database holds nothing that works as one.
        String token = UUID.randomUUID().toString();
        repository.save(PasswordResetToken.builder()
                .token(TokenHash.sha256Hex(token))
                .expiryDate(LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(VALIDITY_MINUTES))
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
     * Sets a new password through an emailed reset link, all or nothing: the
     * link is used up (deleted) and the password changed in one transaction,
     * so a failure leaves the link valid and the password as it was, and of
     * two requests with the same link only the first changes it.
     *
     * @param rawToken    the token from the link
     * @param newPassword the new password, checked by the password policy
     * @throws RuntimeException if the link is unknown, expired or already used,
     *         or the password is refused: nothing is changed
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = getByToken(rawToken);
        if (token.getExpiryDate().isBefore(LocalDateTime.now(ZoneId.systemDefault()))) {
            throw new IllegalStateException("Reset token expired");
        }
        Users user = token.getUser();
        if (repository.deleteToUse(token.getId()) != 1) {
            throw new IllegalStateException("Reset token already used");
        }
        userService.resetPassword(user, newPassword);
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
