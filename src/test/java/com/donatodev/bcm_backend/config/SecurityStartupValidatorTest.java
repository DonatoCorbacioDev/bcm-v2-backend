package com.donatodev.bcm_backend.config;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
class SecurityStartupValidatorTest {

    private static final String VALID_SECRET =
            Base64.getEncoder().encodeToString(new byte[32]); // 32 bytes = 256 bits

    private static final String SHORT_SECRET =
            Base64.getEncoder().encodeToString("tooshort".getBytes()); // < 32 bytes

    private static final String TOTP_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(new byte[48]);

    private SecurityStartupValidator validator(String secret, String mlKey, String mlUrl) {
        return validator(secret, "", TOTP_KEY, mlKey, mlUrl);
    }

    private SecurityStartupValidator validator(String secret, String previous, String totpKey,
                                               String mlKey, String mlUrl) {
        SecurityStartupValidator v = new SecurityStartupValidator();
        ReflectionTestUtils.setField(v, "jwtSecret", secret);
        ReflectionTestUtils.setField(v, "previousJwtSecret", previous);
        ReflectionTestUtils.setField(v, "totpEncryptionKey", totpKey);
        ReflectionTestUtils.setField(v, "mlInternalApiKey", mlKey);
        ReflectionTestUtils.setField(v, "mlFastapiUrl", mlUrl);
        return v;
    }

    @Nested
    @DisplayName("Secrets Management: known, missing, reused and malformed keys")
    @SuppressWarnings("unused")
    class SecretsTests {

        private static final String LOCAL = "http://localhost:8000";

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(
                "com.donatodev.bcm_backend.config.SecurityStartupValidatorTest#placeholders")
        @DisplayName("refuses every JWT placeholder published in the project's files")
        void shouldRefusePlaceholders(String placeholder) {
            var v = validator(placeholder, "k", LOCAL);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> v.run(null));
            org.assertj.core.api.Assertions.assertThat(e.getMessage()).contains("placeholder")
                    .doesNotContain(placeholder);
        }

        @Test
        @DisplayName("refuses a JWT secret that is not Base64, naming the variable only")
        void shouldRefuseNonBase64Secret() {
            var v = validator("not base64 at all!", "k", LOCAL);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> v.run(null));
            org.assertj.core.api.Assertions.assertThat(e.getMessage()).isEqualTo("JWT_SECRET must be Base64.");
        }

        @Test
        @DisplayName("refuses a missing or malformed TOTP encryption key")
        void shouldRefuseMissingTotpKey() {
            assertThrows(IllegalStateException.class, () -> validator(VALID_SECRET, "", "", "k", LOCAL).run(null));
            assertThrows(IllegalStateException.class, () -> validator(VALID_SECRET, "", null, "k", LOCAL).run(null));
            assertThrows(IllegalStateException.class, () -> validator(VALID_SECRET, "", SHORT_SECRET, "k", LOCAL).run(null));
            assertThrows(IllegalStateException.class, () -> validator(VALID_SECRET, "", "%%%", "k", LOCAL).run(null));
        }

        @Test
        @DisplayName("accepts a previous JWT secret during a rotation, refuses it when reused or short")
        void shouldCheckThePreviousSecret() {
            assertDoesNotThrow(() -> validator(VALID_SECRET, OTHER_SECRET, TOTP_KEY, "k", LOCAL).run(null));
            assertDoesNotThrow(() -> validator(VALID_SECRET, null, TOTP_KEY, "k", LOCAL).run(null));
            assertThrows(IllegalStateException.class,
                    () -> validator(VALID_SECRET, VALID_SECRET, TOTP_KEY, "k", LOCAL).run(null));
            assertThrows(IllegalStateException.class,
                    () -> validator(VALID_SECRET, SHORT_SECRET, TOTP_KEY, "k", LOCAL).run(null));
        }
    }

    static java.util.stream.Stream<String> placeholders() {
        return SecurityStartupValidator.KNOWN_PLACEHOLDERS.stream();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: SecurityStartupValidator")
    @SuppressWarnings("unused")
    class ValidatorTests {

        @Test
        @Order(1)
        @DisplayName("passes when JWT key is 32 bytes and ML key is set")
        void shouldPassWithValidConfig() {
            var v = validator(VALID_SECRET, "strong-key", "http://localhost:8000");
            assertDoesNotThrow(() -> v.run(null));
        }

        @Test
        @Order(2)
        @DisplayName("fails when JWT secret decodes to fewer than 32 bytes")
        void shouldFailWhenJwtSecretTooShort() {
            var v = validator(SHORT_SECRET, "strong-key", "http://localhost:8000");
            assertThrows(IllegalStateException.class, () -> v.run(null));
        }

        @Test
        @Order(3)
        @DisplayName("fails when ML URL is non-local and API key is empty")
        void shouldFailWhenMlUrlNonLocalAndKeyMissing() {
            var v = validator(VALID_SECRET, "", "http://ml-service.internal:8000");
            assertThrows(IllegalStateException.class, () -> v.run(null));
        }

        @Test
        @Order(4)
        @DisplayName("passes when ML URL is localhost and API key is empty")
        void shouldPassWhenMlUrlLocalAndKeyMissing() {
            var v = validator(VALID_SECRET, "", "http://localhost:8000");
            assertDoesNotThrow(() -> v.run(null));
        }

        @Test
        @Order(5)
        @DisplayName("passes when ML URL is 127.0.0.1 and API key is empty")
        void shouldPassWhenMlUrl127AndKeyMissing() {
            var v = validator(VALID_SECRET, "", "http://127.0.0.1:8000");
            assertDoesNotThrow(() -> v.run(null));
        }

        @Test
        @Order(6)
        @DisplayName("passes when ML URL is non-local but API key is set")
        void shouldPassWhenMlUrlNonLocalAndKeySet() {
            var v = validator(VALID_SECRET, "strong-secret", "http://ml-service.internal:8000");
            assertDoesNotThrow(() -> v.run(null));
        }
    }
}
