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
