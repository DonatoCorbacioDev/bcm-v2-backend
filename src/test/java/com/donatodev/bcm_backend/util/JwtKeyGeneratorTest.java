package com.donatodev.bcm_backend.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link com.donatodev.bcm_backend.util.JwtKeyGenerator} utility class.
 * <p>
 * This class ensures that:
 * <ul>
 *   <li>The main method executes without error (e.g., for manual key generation).</li>
 *   <li>The private constructor is inaccessible via reflection, and throws an {@link UnsupportedOperationException}.</li>
 * </ul>
 */
class JwtKeyGeneratorTest {

    /**
     * Verifies that the static {@code main} method runs without throwing exceptions.
     */
	@Test
	void testMainMethod() {
	    assertDoesNotThrow(() -> JwtKeyGenerator.main(new String[]{}));
	}

    /** A key for HS384, the algorithm JwtUtils pins: 384 bits. */
    @Test
    void generatesAKeyForHs384() {
        byte[] key = java.util.Base64.getDecoder().decode(JwtKeyGenerator.generate());
        org.junit.jupiter.api.Assertions.assertEquals(48, key.length);
        org.junit.jupiter.api.Assertions.assertEquals(io.jsonwebtoken.Jwts.SIG.HS384,
                com.donatodev.bcm_backend.jwt.JwtUtils.algorithmFor(io.jsonwebtoken.security.Keys.hmacShaKeyFor(key)));
    }

    /**
     * Verifies that the private constructor of {@link JwtKeyGenerator} throws
     * {@link UnsupportedOperationException} when accessed via reflection.
     *
     * @throws Exception in case of unexpected reflection issues
     */
    @Test
    void shouldThrowWhenInstantiatingViaReflection() throws Exception {
        Constructor<JwtKeyGenerator> constructor = JwtKeyGenerator.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);
        Throwable cause = thrown.getCause();
        assertTrue(cause instanceof UnsupportedOperationException);
    }
}