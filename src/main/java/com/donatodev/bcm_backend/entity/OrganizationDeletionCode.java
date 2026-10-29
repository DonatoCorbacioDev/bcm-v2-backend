/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The emailed code that confirms an organization deletion request: at most
 * one per organization, valid for a few minutes. Only its hash is stored.
 */
@Entity
@Table(name = "organization_deletion_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationDeletionCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private Long organizationId;

    /** The admin who asked: only they can confirm with this code. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 hex of {@code "delete:" + organizationId + ":" + code}; the code itself is never stored. */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Builder.Default
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "code_sent_at", nullable = false)
    private LocalDateTime codeSentAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
