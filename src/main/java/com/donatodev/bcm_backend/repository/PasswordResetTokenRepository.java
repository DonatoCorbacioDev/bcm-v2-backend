package com.donatodev.bcm_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.donatodev.bcm_backend.entity.PasswordResetToken;
import com.donatodev.bcm_backend.entity.Users;

/**
 * Repository interface for accessing {@link PasswordResetToken} entities.
 * <p>
 * Provides methods to find password reset tokens by token string or associated user.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

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
}
