/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.TermsAcceptance;

/** One element of {@code data/terms_acceptances.json}: the customer's own copy of the evidence of its signature. */
public record TermsAcceptanceExportDTO(
        Long id,
        String organizationName,
        Long userId,
        String username,
        String signerEmail,
        String termsVersion,
        String onerousClauses,
        boolean professionalUseDeclared,
        String signatureMethod,
        LocalDateTime codeSentAt,
        LocalDateTime acceptedAt,
        String requestIp,
        String requestUserAgent,
        String confirmIp,
        String confirmUserAgent) {

    public static TermsAcceptanceExportDTO from(TermsAcceptance acceptance) {
        return new TermsAcceptanceExportDTO(
                acceptance.getId(),
                acceptance.getOrganizationName(),
                acceptance.getUserId(),
                acceptance.getUsername(),
                acceptance.getSignerEmail(),
                acceptance.getTermsVersion(),
                acceptance.getOnerousClauses(),
                acceptance.isProfessionalUseDeclared(),
                acceptance.getSignatureMethod(),
                acceptance.getCodeSentAt(),
                acceptance.getAcceptedAt(),
                acceptance.getRequestIp(),
                acceptance.getRequestUserAgent(),
                acceptance.getConfirmIp(),
                acceptance.getConfirmUserAgent());
    }
}
