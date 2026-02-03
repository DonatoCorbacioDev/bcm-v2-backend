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
     * Sends a generic email with custom subject and body.
     *
     * @param to the recipient's email address
     * @param subject the email subject
     * @param body the email body (can be HTML)
     */
    void sendEmail(String to, String subject, String body);
}
