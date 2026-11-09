package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit tests for {@link DummyEmailService}.
 * <p>
 * Verifies that the dummy email service correctly logs email operations without
 * actually sending emails (used in test environment).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test: DummyEmailService")
@ActiveProfiles("test")
class DummyEmailServiceTest {

    @InjectMocks
    private DummyEmailService dummyEmailService;

    @Test
    @DisplayName("Should log reset password email without throwing exceptions")
    void shouldLogResetPasswordEmail() {
        assertDoesNotThrow(()
                -> dummyEmailService.sendResetPasswordEmail("test@example.com", "http://reset-link")
        );
    }

    @Test
    @DisplayName("Should log generic email without throwing exceptions")
    void shouldLogGenericEmail() {

        String to = "recipient@example.com";
        String subject = "Test Subject";
        String body = "<h1>Test Body</h1>";

        assertDoesNotThrow(()
                -> dummyEmailService.sendEmail(to, subject, body)
        );
    }

    @Test
    @DisplayName("Should handle null parameters gracefully")
    void shouldHandleNullParameters() {
        assertDoesNotThrow(() -> {
            dummyEmailService.sendEmail(null, null, null);
            dummyEmailService.sendResetPasswordEmail(null, null);
        });
    }

    @Test
    @DisplayName("Should handle empty strings gracefully")
    void shouldHandleEmptyStrings() {
        assertDoesNotThrow(() -> {
            dummyEmailService.sendEmail("", "", "");
            dummyEmailService.sendResetPasswordEmail("", "");
        });
    }

    @Test
    @DisplayName("Should not log when INFO/DEBUG levels are disabled")
    void shouldNotLogWhenLoggingDisabled() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(DummyEmailService.class);
        ch.qos.logback.classic.Level original = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.WARN);
        try {
            assertDoesNotThrow(() -> {
                dummyEmailService.sendResetPasswordEmail("a@b.com", "http://reset");
                dummyEmailService.sendEmail("a@b.com", "Subject", "Body");
                dummyEmailService.sendRegistrationCode("a@b.com", "123456", 15);
                dummyEmailService.sendOrganizationDeletionCode("a@b.com", "Acme", "123456", 15);
                dummyEmailService.sendOrganizationDeletionScheduled("a@b.com", "Acme", "admin",
                        java.time.LocalDate.of(2026, java.time.Month.NOVEMBER, 28));
                dummyEmailService.sendOrganizationDeletionCancelled("a@b.com", "Acme", "admin");
                dummyEmailService.sendOrganizationDeleted("a@b.com", "Acme");
            });
        } finally {
            logger.setLevel(original);
        }
    }

    @Test
    @DisplayName("Should log the organization deletion emails without throwing")
    void shouldLogOrganizationDeletionEmails() {
        assertDoesNotThrow(() -> {
            dummyEmailService.sendOrganizationDeletionCode("a@b.com", "Acme\r\nX", "123456", 15);
            dummyEmailService.sendOrganizationDeletionScheduled("a@b.com", "Acme", "admin",
                    java.time.LocalDate.of(2026, java.time.Month.NOVEMBER, 28));
            dummyEmailService.sendOrganizationDeletionCancelled("a@b.com", "Acme", "admin");
                dummyEmailService.sendOrganizationDeleted("a@b.com", "Acme");
        });
    }

    @Test
    @DisplayName("Scheduled deletion email logs stay on one line, including malicious and null inputs")
    void shouldSanitizeScheduledDeletionLog() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(DummyEmailService.class);
        ch.qos.logback.classic.Level original = logger.getLevel();
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        try {
            dummyEmailService.sendOrganizationDeletionScheduled(
                    "recipient\r\nforged", "org\nforged", "admin\rforged",
                    java.time.LocalDate.of(2026, java.time.Month.NOVEMBER, 28));
            dummyEmailService.sendOrganizationDeletionScheduled(null, null, null, null);

            assertEquals(2, appender.list.size());
            assertEquals("[TEST] Fake deletion scheduled email sent to: recipient__forged "
                    + "for org: org_forged by: admin_forged on: 2026-11-28",
                    appender.list.get(0).getFormattedMessage());
            assertEquals("[TEST] Fake deletion scheduled email sent to: null "
                    + "for org: null by: null on: null",
                    appender.list.get(1).getFormattedMessage());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(original);
        }
    }

    @Test
    @DisplayName("Should log debug body when DEBUG level is enabled")
    void shouldLogDebugWhenDebugEnabled() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(DummyEmailService.class);
        ch.qos.logback.classic.Level original = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            assertDoesNotThrow(() ->
                dummyEmailService.sendEmail("a@b.com", "Subject", "Body")
            );
        } finally {
            logger.setLevel(original);
        }
    }
}
