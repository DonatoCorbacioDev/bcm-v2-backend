package com.donatodev.bcm_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a system user account.
 * <p>
 * Each user can be assigned to a {@link Managers} profile and a {@link Roles} role.
 * Passwords must always be securely stored using hash algorithms.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Users {

    /**
     * Primary key for the user.
     * Auto-incremented ID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique username used for login and identification.
     * Cannot be null.
     */
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /**
     * Securely hashed password.
     * Never store plain-text passwords!
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * One-to-one relationship with a manager profile (optional).
     * <p>
     * If present, the user is linked to a manager entity.
     */
    @OneToOne
    @JoinColumn(name = "manager_id", nullable = true)
    private Managers manager;

    /**
     * User role association (e.g., ADMIN, MANAGER).
     * Required field.
     */
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Roles role;

    /**
     * Indicates whether the user has completed email verification or account activation.
     * Default value is false.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean verified = false;
}
