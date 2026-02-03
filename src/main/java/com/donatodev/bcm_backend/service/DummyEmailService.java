package com.donatodev.bcm_backend.service;

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
@Profile("test")
@Primary
public class DummyEmailService implements IEmailService {

    private static final Logger logger = LoggerFactory.getLogger(DummyEmailService.class);

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
        logger.info("[TEST] Fake verification email sent to: {} with link: {}", to, verificationLink);
    }

    /**
     * Simulates sending a password reset email by logging to the console.
     *
     * @param to the recipient email address
     * @param resetLink the password reset link to include in the email
     */
    @Override
    public void sendResetPasswordEmail(String to, String resetLink) {
        logger.info("[TEST] Fake password reset email sent to: {} with link: {}", to, resetLink);
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
        logger.info("[TEST] Fake email sent to: {} with subject: {}", to, subject);
        logger.debug("[TEST] Email body: {}", body);
    }
}
