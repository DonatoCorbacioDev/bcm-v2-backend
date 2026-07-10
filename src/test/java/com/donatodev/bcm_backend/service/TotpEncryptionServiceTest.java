package com.donatodev.bcm_backend.service;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class TotpEncryptionServiceTest {

    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes());

    private TotpEncryptionService encryptionService;

    @BeforeEach
    void setup() {
        encryptionService = new TotpEncryptionService();
        ReflectionTestUtils.setField(encryptionService, "jwtSecret", JWT_SECRET);
    }

    @Test
    @DisplayName("decrypt(encrypt(x)) returns the original plaintext")
    void shouldRoundTrip() {
        String plain = "JBSWY3DPEHPK3PXP";
        String encrypted = encryptionService.encrypt(plain);

        assertNotEquals(plain, encrypted);
        assertEquals(plain, encryptionService.decrypt(encrypted));
    }

    @Test
    @DisplayName("encrypting the same plaintext twice yields different ciphertext (random IV)")
    void shouldUseRandomIv() {
        String plain = "JBSWY3DPEHPK3PXP";
        assertNotEquals(encryptionService.encrypt(plain), encryptionService.encrypt(plain));
    }

    @Test
    @DisplayName("decrypt fails on tampered ciphertext (GCM authentication)")
    void shouldRejectTamperedCiphertext() {
        String encrypted = encryptionService.encrypt("JBSWY3DPEHPK3PXP");
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 0x01; // flip a bit in the ciphertext
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThrows(IllegalStateException.class, () -> encryptionService.decrypt(tampered));
    }

    @Test
    @DisplayName("a different key cannot decrypt data encrypted with another key")
    void shouldNotDecryptWithDifferentKey() {
        String encrypted = encryptionService.encrypt("JBSWY3DPEHPK3PXP");

        TotpEncryptionService otherService = new TotpEncryptionService();
        ReflectionTestUtils.setField(otherService, "jwtSecret",
                Base64.getEncoder().encodeToString("differentsecretdifferentsecret!!".getBytes()));

        assertThrows(IllegalStateException.class, () -> otherService.decrypt(encrypted));
    }

    @Test
    @DisplayName("encrypt wraps a GeneralSecurityException as IllegalStateException")
    void shouldWrapEncryptionFailure() {
        try (MockedStatic<Cipher> mockedCipher = Mockito.mockStatic(Cipher.class)) {
            mockedCipher.when(() -> Cipher.getInstance(anyString()))
                    .thenThrow(new NoSuchAlgorithmException("no such algorithm"));

            assertThrows(IllegalStateException.class, () -> encryptionService.encrypt("JBSWY3DPEHPK3PXP"));
        }
    }
}
