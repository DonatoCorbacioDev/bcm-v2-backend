/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
 * Each user can be assigned to a {@link Managers} profile and a {@link Roles}
 * role. Passwords must always be securely stored using hash algorithms.
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
     * Primary key for the user. Auto-incremented ID.
     */
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /**
     * Unique username used for login and identification. Cannot be null.
     */
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /**
     * Securely hashed password. Never store plain-text passwords!
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
     * User role association (e.g., ADMIN, MANAGER). Required field.
     */
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Roles role;

    /**
     * Indicates whether the user has completed email verification or account
     * activation. Default value is false.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean verified = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /**
     * Unguessable token used to authenticate the public .ics calendar feed
     * URL (calendar apps subscribe by URL and cannot send a bearer token).
     * Lazily generated on first request, rotatable by the user.
     */
    @Column(name = "calendar_token", unique = true, length = 64)
    private String calendarToken;

    /**
     * AES-GCM encrypted TOTP secret (base64). Present once the user has
     * started or completed 2FA enrollment; only meaningful together with
     * {@link #totpEnabled}.
     */
    @Column(name = "totp_secret_encrypted", length = 255)
    private String totpSecretEncrypted;

    @Builder.Default
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    /**
     * The TOTP time step of the last code accepted at login. A code is
     * accepted only for a later step, so a code seen by someone else (over a
     * shoulder, in a phishing proxy) can't be used again while still valid.
     */
    @Column(name = "totp_last_used_step")
    private Long totpLastUsedStep;

    /**
     * When the credentials last changed (password, 2FA on or off), as epoch
     * seconds. An access token issued in an earlier second is refused
     * ({@link com.donatodev.bcm_backend.jwt.JwtUtils}); null means no change
     * recorded, so no token is refused for this reason.
     */
    @Column(name = "credentials_changed_at")
    private Long credentialsChangedAt;

    /**
     * Whether this user can approve or reject contracts submitted for
     * review, in addition to ADMIN (who can always approve/reject).
     */
    @Builder.Default
    @Column(name = "can_approve_contracts", nullable = false)
    private boolean canApproveContracts = false;

    /**
     * Count of consecutive failed login attempts since the last successful
     * login (or since the account was last unlocked). Reset to 0 on success.
     */
    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /**
     * If set and in the future, login is refused regardless of a correct
     * password (see {@link com.donatodev.bcm_backend.auth.AuthService}).
     * Cleared automatically once it has passed.
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
}
