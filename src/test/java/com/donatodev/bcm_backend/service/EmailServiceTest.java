package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Unit tests for {@link EmailService}.
 * <p>
 * Covers scenarios for sending verification and reset password emails. Verifies
 * successful email sending and proper exception handling on failure.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test: EmailService")
@ActiveProfiles("test")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    /**
     * Should send verification email without throwing exceptions.
     */
    @Test
    @Order(1)
    @DisplayName("Should send verification email successfully")
    void shouldSendVerificationEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertDoesNotThrow(()
                -> emailService.sendVerificationEmail("test@example.com", "http://verify-link"));

        verify(mailSender).send(mimeMessage);
    }

    /**
     * Should send reset password email without throwing exceptions.
     */
    @Test
    @Order(2)
    @DisplayName("Should send reset password email successfully")
    void shouldSendResetPasswordEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertDoesNotThrow(()
                -> emailService.sendResetPasswordEmail("test@example.com", "http://reset-link"));

        verify(mailSender).send(mimeMessage);
    }

    /**
     * Should throw RuntimeException when verification email fails to send.
     */
    @Test
    @Order(3)
    @DisplayName("Should throw exception if verification email fails")
    void shouldThrowExceptionOnVerificationEmailFailure() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        doAnswer(invocation -> {
            throw new MessagingException("boom");
        }).when(mailSender).send(mimeMessage);

        RuntimeException ex = assertThrows(RuntimeException.class, ()
                -> emailService.sendVerificationEmail("test@example.com", "http://fail"));

        assertTrue(ex.getMessage().contains("Error sending verification email"));
    }

    /**
     * Should throw RuntimeException when reset password email fails to send.
     */
    @Test
    @Order(4)
    @DisplayName("Should throw exception if reset password email fails")
    void shouldThrowExceptionOnResetPasswordEmailFailure() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        doAnswer(invocation -> {
            throw new MessagingException("boom");
        }).when(mailSender).send(mimeMessage);

        RuntimeException ex = assertThrows(RuntimeException.class, ()
                -> emailService.sendResetPasswordEmail("test@example.com", "http://fail"));

        assertTrue(ex.getMessage().contains("Error sending reset password email"));
    }

    /**
     * Should send generic email without throwing exceptions.
     */
    @Test
    @Order(5)
    @DisplayName("Should send generic email successfully")
    void shouldSendGenericEmail() {

        String to = "recipient@example.com";
        String subject = "Test Subject";
        String body = "<h1>Test Body</h1><p>This is a test email.</p>";

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertDoesNotThrow(()
                -> emailService.sendEmail(to, subject, body)
        );

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }

    /**
     * Should throw RuntimeException when generic email fails to send.
     */
    @Test
    @Order(6)
    @DisplayName("Should throw exception when send generic email fails")
    void shouldThrowExceptionWhenSendGenericEmailFails() {

        String to = "recipient@example.com";
        String subject = "Test Subject";
        String body = "Test body";

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        doAnswer(invocation -> {
            throw new MessagingException("Mail server error");
        }).when(mailSender).send(mimeMessage);

        RuntimeException ex = assertThrows(RuntimeException.class, ()
                -> emailService.sendEmail(to, subject, body)
        );

        assertTrue(ex.getMessage().contains("Error sending email"));
    }
}
