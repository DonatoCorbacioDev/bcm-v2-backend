package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.entity.VerificationToken;
import com.donatodev.bcm_backend.repository.VerificationTokenRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service class for managing verification tokens.
 * <p>
 * Provides methods to create, retrieve, and delete email verification tokens.
 */
@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    private final VerificationTokenRepository tokenRepository;

    /**
     * Creates a new verification token for the given user.
     * The token expires 24 hours after creation.
     *
     * @param user the user to associate with the token
     * @return the created {@link VerificationToken}
     */
    public VerificationToken createToken(Users user) {
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .user(user)
                .build();
        return tokenRepository.save(verificationToken);
    }

    /**
     * Retrieves a verification token by its token string.
     *
     * @param token the token string to search for
     * @return the matching {@link VerificationToken}
     * @throws RuntimeException if the token is invalid or not found
     */
    public VerificationToken getByToken(String token) {
        return tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));
    }

    /**
     * Deletes a given verification token.
     *
     * @param token the {@link VerificationToken} to delete
     */
    public void deleteToken(VerificationToken token) {
        tokenRepository.delete(token);
    }
}
