package com.donatodev.bcm_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.donatodev.bcm_backend.entity.VerificationToken;

/**
 * Repository interface for accessing {@link VerificationToken} entities.
 * <p>
 * Provides method to find verification tokens by token string.
 */
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /**
     * Finds a {@link VerificationToken} entity by its token string.
     *
     * @param token the token string to search for
     * @return an {@link Optional} containing the matching token if found, or empty otherwise
     */
    Optional<VerificationToken> findByToken(String token);
}
