/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

/**
 * Interface defining email service operations.
 * <p>
 * Provides methods to send verification and password reset emails.
 */
public interface IEmailService {

    /**
     * Sends an account verification email.
     *
     * @param to the recipient's email address
     * @param verificationLink the verification link to be included in the email
     */
    void sendVerificationEmail(String to, String verificationLink);

    /**
     * Sends a password reset email.
     *
     * @param to the recipient's email address
     * @param resetLink the password reset link to be included in the email
     */
    void sendResetPasswordEmail(String to, String resetLink);

    /**
     * Sends the one-time code that confirms a self-service registration and
     * signs the specific approval of the Terms' onerous clauses.
     *
     * @param to               the future administrator's email address
     * @param code             the 6-digit code to type back
     * @param validityMinutes  how long the code stays valid
     */
    void sendRegistrationCode(String to, String code, int validityMinutes);

    /**
     * Sends a generic email with custom subject and body.
     *
     * @param to the recipient's email address
     * @param subject the email subject
     * @param body the email body (can be HTML)
     */
    void sendEmail(String to, String subject, String body);
}
