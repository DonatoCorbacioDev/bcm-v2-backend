package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Cipher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** Two-factor secrets at rest: their own key, the old format still read during the move. */
class TotpEncryptionServiceTest {

    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes());
    private static final String OTHER_JWT_SECRET = Base64.getEncoder().encodeToString(
            "differentsecretdifferentsecret!!".getBytes());
    private static final String TOTP_KEY = Base64.getEncoder().encodeToString(
            "abcdefghijabcdefghijabcdefghij12".getBytes());
    private static final String PLAIN = "JBSWY3DPEHPK3PXP";

    private static TotpEncryptionService withKey() {
        return new TotpEncryptionService(TOTP_KEY, JWT_SECRET, "");
    }

    private static TotpEncryptionService legacyOnly(String jwtSecret) {
        return new TotpEncryptionService("", jwtSecret, "");
    }

    @Test
    @DisplayName("with the dedicated key, values are stored as v2: and read back")
    void shouldRoundTripWithTheDedicatedKey() {
        String encrypted = withKey().encrypt(PLAIN);

        assertThat(encrypted).startsWith("v2:").doesNotContain(PLAIN);
        assertThat(withKey().decrypt(encrypted)).isEqualTo(PLAIN);
    }

    /**
     * The layout did not change when the IV and the ciphertext stopped being
     * joined through a length sum (CodeQL java/uncontrolled-arithmetic): a
     * 12-byte IV, then the ciphertext with its 16-byte tag. A value built the
     * old way, by hand here, is still read, so the secrets already stored are.
     */
    @Test
    @DisplayName("the stored layout is unchanged: IV then ciphertext, and values written before still read")
    void layoutIsUnchanged() throws Exception {
        byte[] stored = Base64.getDecoder().decode(withKey().encrypt(PLAIN).substring("v2:".length()));
        assertThat(stored).hasSize(12 + PLAIN.length() + 16);

        byte[] iv = "fixed-iv-12b".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(TOTP_KEY), "AES"),
                new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] cipherText = cipher.doFinal(PLAIN.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.ByteBuffer before = java.nio.ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText);

        assertThat(withKey().decrypt("v2:" + Base64.getEncoder().encodeToString(before.array()))).isEqualTo(PLAIN);
    }

    @Nested
    @DisplayName("rotating TOTP_ENCRYPTION_KEY")
    class Rotation {

        private final String newKey = Base64.getEncoder().encodeToString("ZYXWVUTSRQZYXWVUTSRQZYXWVUTSRQ98".getBytes());

        @Test
        @DisplayName("a value under the previous key is still read, and flagged for re-encryption")
        void previousKeyStillReads() {
            String underOld = withKey().encrypt(PLAIN);
            TotpEncryptionService rotating = new TotpEncryptionService(newKey, TOTP_KEY, JWT_SECRET, "");

            assertThat(rotating.decrypt(underOld)).isEqualTo(PLAIN);
            assertThat(rotating.needsReEncryption(underOld)).isTrue();
            assertThat(rotating.needsReEncryption(rotating.encrypt(PLAIN))).isFalse();
        }

        @Test
        @DisplayName("new values go under the new key, which alone reads them")
        void newValuesUnderTheNewKey() {
            TotpEncryptionService rotating = new TotpEncryptionService(newKey, TOTP_KEY, JWT_SECRET, "");
            String stored = rotating.encrypt(PLAIN);

            assertThat(new TotpEncryptionService(newKey, JWT_SECRET, "").decrypt(stored)).isEqualTo(PLAIN);
            assertThatThrownBy(() -> withKey().decrypt(stored)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("a value under neither key is refused, not read as something else")
        void neitherKey() {
            String other = Base64.getEncoder().encodeToString("QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ".getBytes());
            String underOther = new TotpEncryptionService(other, JWT_SECRET, "").encrypt(PLAIN);
            TotpEncryptionService rotating = new TotpEncryptionService(newKey, TOTP_KEY, JWT_SECRET, "");

            assertThatThrownBy(() -> rotating.decrypt(underOther)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("a malformed previous key is refused at startup, naming it")
        void malformedPreviousKey() {
            assertThatThrownBy(() -> new TotpEncryptionService(newKey, "dG9vc2hvcnQ=", JWT_SECRET, ""))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TOTP_ENCRYPTION_KEY_PREVIOUS");
        }
    }

    @Test
    @DisplayName("the dedicated key does not depend on the JWT secret: rotating it changes nothing")
    void shouldSurviveAJwtKeyRotation() {
        String encrypted = withKey().encrypt(PLAIN);

        assertThat(new TotpEncryptionService(TOTP_KEY, OTHER_JWT_SECRET, "").decrypt(encrypted)).isEqualTo(PLAIN);
    }

    @Test
    @DisplayName("a value in the old format is still read, and flagged for re-encryption")
    void shouldReadTheOldFormat() {
        String old = legacyOnly(JWT_SECRET).encrypt(PLAIN);

        assertThat(old).doesNotStartWith("v2:");
        assertThat(withKey().decrypt(old)).isEqualTo(PLAIN);
        assertThat(withKey().needsReEncryption(old)).isTrue();
        assertThat(withKey().needsReEncryption(withKey().encrypt(PLAIN))).isFalse();
        assertThat(legacyOnly(JWT_SECRET).needsReEncryption(old)).isFalse();
    }

    @Test
    @DisplayName("an old value is read with the previous JWT secret while it is being rotated")
    void shouldReadTheOldFormatWithThePreviousJwtSecret() {
        String old = legacyOnly(JWT_SECRET).encrypt(PLAIN);

        assertThat(new TotpEncryptionService(TOTP_KEY, OTHER_JWT_SECRET, JWT_SECRET).decrypt(old)).isEqualTo(PLAIN);
        assertThatThrownBy(() -> new TotpEncryptionService(TOTP_KEY, OTHER_JWT_SECRET, "").decrypt(old))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a v2 value cannot be read without the dedicated key")
    void shouldRefuseANewValueWithoutTheKey() {
        String encrypted = withKey().encrypt(PLAIN);

        assertThatThrownBy(() -> legacyOnly(JWT_SECRET).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("encrypting the same plaintext twice yields different ciphertext (random IV)")
    void shouldUseRandomIv() {
        assertThat(withKey().encrypt(PLAIN)).isNotEqualTo(withKey().encrypt(PLAIN));
    }

    @Test
    @DisplayName("decrypt fails on tampered ciphertext (GCM authentication)")
    void shouldRejectTamperedCiphertext() {
        String encrypted = withKey().encrypt(PLAIN);
        byte[] bytes = Base64.getDecoder().decode(encrypted.substring(3));
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = "v2:" + Base64.getEncoder().encodeToString(bytes);

        assertThrows(IllegalStateException.class, () -> withKey().decrypt(tampered));
        assertThrows(IllegalStateException.class, () -> withKey().decrypt("v2:not base64!"));
    }

    @Test
    @DisplayName("a different dedicated key cannot decrypt")
    void shouldNotDecryptWithDifferentKey() {
        String encrypted = withKey().encrypt(PLAIN);
        String otherKey = Base64.getEncoder().encodeToString("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".getBytes());

        assertThrows(IllegalStateException.class,
                () -> new TotpEncryptionService(otherKey, JWT_SECRET, "").decrypt(encrypted));
    }

    @Test
    @DisplayName("a malformed dedicated key is refused at startup")
    void shouldRefuseAMalformedKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new TotpEncryptionService(shortKey, JWT_SECRET, ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new TotpEncryptionService("not base64!", JWT_SECRET, ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
        assertThat(new TotpEncryptionService("  ", JWT_SECRET, null).hasDedicatedKey()).isFalse();
    }

    @Test
    @DisplayName("encrypt wraps a GeneralSecurityException as IllegalStateException")
    void shouldWrapEncryptionFailure() {
        TotpEncryptionService service = withKey();
        try (MockedStatic<Cipher> mockedCipher = Mockito.mockStatic(Cipher.class)) {
            mockedCipher.when(() -> Cipher.getInstance(anyString()))
                    .thenThrow(new NoSuchAlgorithmException("no such algorithm"));

            assertThrows(IllegalStateException.class, () -> service.encrypt(PLAIN));
        }
    }
}
