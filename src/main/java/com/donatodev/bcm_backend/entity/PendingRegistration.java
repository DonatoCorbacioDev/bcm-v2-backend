/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A self-service registration waiting for its emailed confirmation code.
 * Nothing here is an organization or a user yet; the password is stored
 * already hashed. {@code publicId} is the only identifier the client ever
 * sees, so the numeric id can't be enumerated.
 */
@Entity
@Table(name = "pending_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private String adminFirstName;

    @Column(nullable = false)
    private String adminLastName;

    @Column(nullable = false)
    private String adminEmail;

    @Column(nullable = false)
    private String adminUsername;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String termsVersion;

    /** SHA-256 hex of {@code publicId + ":" + code}; the code itself is never stored. */
    @Column(nullable = false, length = 64)
    private String codeHash;

    @Builder.Default
    @Column(nullable = false)
    private int failedAttempts = 0;

    @Builder.Default
    @Column(nullable = false)
    private int codesSent = 1;

    @Column(nullable = false)
    private LocalDateTime codeSentAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(length = 45)
    private String requestIp;

    private String requestUserAgent;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.systemDefault());
    }
}
