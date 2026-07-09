package com.donatodev.bcm_backend.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.net.URLEncoder;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;

/**
 * Minimal RFC 6238 (TOTP) / RFC 4226 (HOTP) implementation: HmacSHA1,
 * 6-digit codes, 30-second time step, +/-1 step tolerance for clock drift.
 */
public final class TotpUtil {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int SECRET_BYTES = 20;
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    /** Generates a new random Base32-encoded secret (no padding). */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return new Base32().encodeToString(bytes).replace("=", "");
    }

    /** Builds the otpauth:// URI an authenticator app scans as a QR code. */
    public static String buildOtpAuthUri(String secretBase32, String accountName, String issuer) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String encodedAccount = URLEncoder.encode(accountName, StandardCharsets.UTF_8);
        return "otpauth://totp/" + encodedIssuer + ":" + encodedAccount
                + "?secret=" + secretBase32
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=" + CODE_DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    /** Verifies a 6-digit code against the secret, tolerating +/-1 time step of clock drift. */
    public static boolean verifyCode(String secretBase32, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (long step = currentStep - ALLOWED_DRIFT_STEPS; step <= currentStep + ALLOWED_DRIFT_STEPS; step++) {
            if (code.equals(generateCode(secretBase32, step))) {
                return true;
            }
        }
        return false;
    }

    private static String generateCode(String secretBase32, long timeStep) {
        byte[] key = new Base32().decode(padBase32(secretBase32));
        byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute TOTP code", e);
        }
    }

    private static String padBase32(String secret) {
        int remainder = secret.length() % 8;
        if (remainder == 0) {
            return secret;
        }
        return secret + "=".repeat(8 - remainder);
    }
}
