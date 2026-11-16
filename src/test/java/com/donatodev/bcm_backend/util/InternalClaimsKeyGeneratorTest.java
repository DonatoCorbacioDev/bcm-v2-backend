package com.donatodev.bcm_backend.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link InternalClaimsKeyGenerator} utility class, mirroring
 * {@link JwtKeyGeneratorTest}'s pattern for the same kind of run-once CLI utility.
 */
class InternalClaimsKeyGeneratorTest {

    @Test
    void testMainMethod() {
        assertDoesNotThrow(() -> InternalClaimsKeyGenerator.main(new String[]{}));
    }

    /** One RSA pair, at least 2048 bits, in the formats the backend and the ML service read. */
    @Test
    void generatesAMatchingRsaPair() throws Exception {
        String[] keys = InternalClaimsKeyGenerator.generate();
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) rsa.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keys[0])));
        RSAPublicKey publicKey = (RSAPublicKey) rsa.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(keys[1])));

        assertEquals(publicKey.getModulus(), privateKey.getModulus());
        assertTrue(publicKey.getModulus().bitLength() >= 2048);
    }

    @Test
    void shouldThrowWhenInstantiatingViaReflection() throws Exception {
        Constructor<InternalClaimsKeyGenerator> constructor = InternalClaimsKeyGenerator.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);
        Throwable cause = thrown.getCause();
        assertTrue(cause instanceof UnsupportedOperationException);
    }
}
