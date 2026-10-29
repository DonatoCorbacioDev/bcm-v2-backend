/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Dummy implementation of {@link IEmailService} used for testing purposes.
 * <p>
 * This service is activated only under the "test" profile and provides fake
 * email sending functionality by logging messages to the console.
 */
@Service
@Profile({"test", "dev"})
@Primary
public class DummyEmailService implements IEmailService {

    private static final Logger logger = LoggerFactory.getLogger(DummyEmailService.class);
    private static final String CRLF_REGEX = "[\r\n]";

    /**
     * Constructor that logs activation of the dummy email service.
     */
    public DummyEmailService() {
        logger.info("[TEST] DummyEmailService activated");
    }

    /**
     * Simulates sending a verification email by logging to the console.
     *
     * @param to the recipient email address
     * @param verificationLink the verification link to include in the email
     */
    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        if (logger.isInfoEnabled()) {
            logger.info("[TEST] Fake verification email sent to: {} with link: {}",
                    sanitize(to), sanitize(verificationLink));
        }
    }

    /**
     * Simulates sending a password reset email by logging to the console.
     *
     * @param to the recipient email address
     * @param resetLink the password reset link to include in the email
     */
    @Override
    public void sendResetPasswordEmail(String to, String resetLink) {
        if (logger.isInfoEnabled()) {
            logger.info("[TEST] Fake password reset email sent to: {} with link: {}",
                    sanitize(to), sanitize(resetLink));
        }
    }

    /**
     * Simulates sending the registration code by logging it — dev/test only,
     * and the CI smoke test reads it from this line to confirm its registration.
     *
     * @param to the recipient email address
     * @param code the one-time code
     * @param validityMinutes how long the code stays valid
     */
    @Override
    public void sendRegistrationCode(String to, String code, int validityMinutes) {
        if (logger.isInfoEnabled()) {
            logger.info("[TEST] Fake registration code email sent to: {} with code: {} (valid {} min)",
                    sanitize(to), sanitize(code), validityMinutes);
        }
    }

    @Override
    public void sendOrganizationDeletionCode(String to, String organizationName, String code, int validityMinutes) {
        if (logger.isInfoEnabled()) {
            logger.info("[TEST] Fake organization deletion code email sent to: {} for org: {} with code: {} (valid {} min)",
                    sanitize(to), sanitize(organizationName), sanitize(code), validityMinutes);
        }
    }

    @Override
    public void sendOrganizationDeletionScheduled(String to, String organizationName, String requestedBy,
            LocalDate scheduledFor) {
        if (logger.isInfoEnabled()) {
            logger.info("[TEST] Fake deletion scheduled email sent to: {} for org: {} by: {} on: {}",
                    sanitize(to), sanitize(organizationName), sanitize(requestedBy), scheduledFor);
        }
    }

    @Override
    public void sendOrganizationDeletionCancelled(String to, String organizationName, String cancelledBy) {
        if (logger.isInfoEnabled()) {
            logger.info("[TEST] Fake deletion cancelled email sent to: {} for org: {} by: {}",
                    sanitize(to), sanitize(organizationName), sanitize(cancelledBy));
        }
    }

    /**
     * Simulates sending a generic email by logging to the console.
     *
     * @param to the recipient email address
     * @param subject the email subject
     * @param body the email body
     */
    @Override
    public void sendEmail(String to, String subject, String body) {
        if (logger.isInfoEnabled()) {
            logger.info("[TEST] Fake email sent to: {} with subject: {}",
                    sanitize(to), sanitize(subject));
        }
        if (logger.isDebugEnabled()) {
            logger.debug("[TEST] Email body: {}", sanitize(body));
        }
    }

    private static String sanitize(String value) {
        return value == null ? null : value.replaceAll(CRLF_REGEX, "_");
    }
}
