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
 * Proof that a customer accepted a given version of the Terms of Service and
 * specifically approved its onerous clauses, signed with an emailed one-time
 * code. Identifiers are copied rather than mapped as relations on purpose:
 * the evidence must survive the deletion of the organization and the user
 * (see V47 for the retention reasoning).
 */
@Entity
@Table(name = "terms_acceptances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermsAcceptance {

    /** The only signature method today: a code emailed to the signer and typed back. */
    public static final String SIGNATURE_EMAIL_OTP = "EMAIL_OTP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private String organizationName;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String signerEmail;

    @Column(nullable = false, length = 20)
    private String termsVersion;

    @Column(nullable = false, length = 100)
    private String onerousClauses;

    @Column(nullable = false)
    private boolean professionalUseDeclared;

    @Column(nullable = false, length = 20)
    private String signatureMethod;

    @Column(nullable = false)
    private LocalDateTime codeSentAt;

    @Column(nullable = false)
    private LocalDateTime acceptedAt;

    @Column(length = 45)
    private String requestIp;

    private String requestUserAgent;

    @Column(length = 45)
    private String confirmIp;

    private String confirmUserAgent;
}
