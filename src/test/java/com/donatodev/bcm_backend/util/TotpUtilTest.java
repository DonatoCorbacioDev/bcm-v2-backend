package com.donatodev.bcm_backend.util;

import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import javax.crypto.Mac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class TotpUtilTest {

    // RFC 6238 test vector: ASCII secret "12345678901234567890", HmacSHA1,
    // time step 30s, T=59s -> counter 1 -> 8-digit code "94287082" (6-digit: "287082").
    private static final String RFC_SECRET_BASE32 = new org.apache.commons.codec.binary.Base32()
            .encodeToString("12345678901234567890".getBytes()).replace("=", "");

    private String codeAt(String secret, long timeStep) throws Exception {
        Method m = TotpUtil.class.getDeclaredMethod("generateCode", String.class, long.class);
        m.setAccessible(true);
        return (String) m.invoke(null, secret, timeStep);
    }

    @Test
    @DisplayName("generateCode matches the RFC 6238 test vector (counter 1, 6 digits)")
    void shouldMatchRfcTestVector() throws Exception {
        assertEquals("287082", codeAt(RFC_SECRET_BASE32, 1));
    }

    @Test
    @DisplayName("generateSecret produces a usable, decodable Base32 secret each time")
    void shouldGenerateDifferentValidSecrets() {
        String first = TotpUtil.generateSecret();
        String second = TotpUtil.generateSecret();

        assertNotEquals(first, second);
        assertFalse(first.contains("="));
        assertTrue(first.matches("[A-Z2-7]+"));
    }

    @Test
    @DisplayName("buildOtpAuthUri includes issuer, account, secret and standard TOTP parameters")
    void shouldBuildOtpAuthUri() {
        String uri = TotpUtil.buildOtpAuthUri("ABCDEFGH", "alice", "BCM");

        assertTrue(uri.startsWith("otpauth://totp/BCM:alice?"));
        assertTrue(uri.contains("secret=ABCDEFGH"));
        assertTrue(uri.contains("issuer=BCM"));
        assertTrue(uri.contains("algorithm=SHA1"));
        assertTrue(uri.contains("digits=6"));
        assertTrue(uri.contains("period=30"));
    }

    @Test
    @DisplayName("verifyCode accepts the code currently valid for the secret")
    void shouldAcceptCurrentValidCode() throws Exception {
        String secret = TotpUtil.generateSecret();
        long currentStep = Instant.now().getEpochSecond() / 30;
        String code = codeAt(secret, currentStep);

        assertTrue(TotpUtil.verifyCode(secret, code));
    }

    @Test
    @DisplayName("verifyCode tolerates one time step of clock drift in either direction")
    void shouldToleratePlusMinusOneStepDrift() throws Exception {
        String secret = TotpUtil.generateSecret();
        long currentStep = Instant.now().getEpochSecond() / 30;

        assertTrue(TotpUtil.verifyCode(secret, codeAt(secret, currentStep - 1)));
        assertTrue(TotpUtil.verifyCode(secret, codeAt(secret, currentStep + 1)));
    }

    @Test
    @DisplayName("verifyCode rejects a code two steps away")
    void shouldRejectCodeOutsideDriftWindow() throws Exception {
        String secret = TotpUtil.generateSecret();
        long currentStep = Instant.now().getEpochSecond() / 30;

        assertFalse(TotpUtil.verifyCode(secret, codeAt(secret, currentStep + 2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "1234567", "abcdef", "12 345"})
    @DisplayName("verifyCode rejects anything that isn't exactly 6 digits")
    void shouldRejectMalformedCodes(String code) {
        assertFalse(TotpUtil.verifyCode(TotpUtil.generateSecret(), code));
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("verifyCode rejects null")
    void shouldRejectNullCode(String code) {
        assertFalse(TotpUtil.verifyCode(TotpUtil.generateSecret(), code));
    }

    @Test
    @DisplayName("verifyCode rejects a well-formed but wrong code")
    void shouldRejectWrongCode() {
        String secret = TotpUtil.generateSecret();
        assertFalse(TotpUtil.verifyCode(secret, "000000"));
    }

    @Test
    @DisplayName("generateCode pads a Base32 secret whose length isn't a multiple of 8")
    void shouldPadSecretNotMultipleOfEight() throws Exception {
        assertTrue(codeAt("MZXW6", 1).matches("\\d{6}"));
    }

    @Test
    @DisplayName("generateCode wraps a Mac failure as IllegalStateException")
    void shouldWrapMacFailure() {
        String secret = TotpUtil.generateSecret();
        try (MockedStatic<Mac> mockedMac = Mockito.mockStatic(Mac.class)) {
            mockedMac.when(() -> Mac.getInstance(anyString()))
                    .thenThrow(new NoSuchAlgorithmException("no such algorithm"));

            assertThrows(IllegalStateException.class, () -> TotpUtil.verifyCode(secret, "123456"));
        }
    }
}
