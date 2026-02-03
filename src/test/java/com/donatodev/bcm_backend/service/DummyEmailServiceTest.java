package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @DisplayName("Should log verification email without throwing exceptions")
    void shouldLogVerificationEmail() {
        assertDoesNotThrow(()
                -> dummyEmailService.sendVerificationEmail("test@example.com", "http://verify-link")
        );
    }

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
            dummyEmailService.sendVerificationEmail(null, null);
            dummyEmailService.sendResetPasswordEmail(null, null);
        });
    }

    @Test
    @DisplayName("Should handle empty strings gracefully")
    void shouldHandleEmptyStrings() {
        assertDoesNotThrow(() -> {
            dummyEmailService.sendEmail("", "", "");
            dummyEmailService.sendVerificationEmail("", "");
            dummyEmailService.sendResetPasswordEmail("", "");
        });
    }
}
