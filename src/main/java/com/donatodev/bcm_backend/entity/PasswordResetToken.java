package com.donatodev.bcm_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a password reset token associated with a specific user.
 * <p>
 * This entity is used to handle secure password reset functionality by
 * storing a temporary token with an expiration time.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    /**
     * Unique identifier for the password reset token.
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The token string used to identify the password reset request.
     */
    private String token;

    /**
     * The expiry date and time of the token.
     * After this timestamp, the token is no longer valid.
     */
    private LocalDateTime expiryDate;

    /**
     * One-to-one relationship with the {@link Users} entity.
     * <p>
     * Each token is associated with a single user.
     */
    @OneToOne
    @JoinColumn(name = "user_id")
    private Users user;
}
