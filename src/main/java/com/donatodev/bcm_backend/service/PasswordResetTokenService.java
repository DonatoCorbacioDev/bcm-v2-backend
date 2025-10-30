package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.PasswordResetToken;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.PasswordResetTokenRepository;

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
    public PasswordResetToken createToken(Users user) {
        repository.findByUser(user).ifPresent(repository::delete);

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .expiryDate(LocalDateTime.now().plusMinutes(30))
                .user(user)
                .build();
        return repository.save(resetToken);
    }

    /**
     * Retrieves a password reset token by its token string.
     *
     * @param token the token string to search for
     * @return the corresponding {@link PasswordResetToken}
     * @throws RuntimeException if the token is not found
     */
    public PasswordResetToken getByToken(String token) {
        return repository.findByToken(token)
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
