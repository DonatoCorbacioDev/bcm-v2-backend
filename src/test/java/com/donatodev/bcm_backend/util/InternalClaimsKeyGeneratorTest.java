package com.donatodev.bcm_backend.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * Unit tests for the {@link InternalClaimsKeyGenerator} utility class, mirroring
 * {@link JwtKeyGeneratorTest}'s pattern for the same kind of run-once CLI utility.
 */
class InternalClaimsKeyGeneratorTest {

    @Test
    void testMainMethod() {
        assertDoesNotThrow(() -> InternalClaimsKeyGenerator.main(new String[]{}));
    }

    @Test
    void testMainMethodWithInfoLoggingDisabled() {
        ch.qos.logback.classic.Logger keyGeneratorLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(InternalClaimsKeyGenerator.class);
        Level originalLevel = keyGeneratorLogger.getLevel();
        keyGeneratorLogger.setLevel(Level.OFF);
        try {
            assertDoesNotThrow(() -> InternalClaimsKeyGenerator.main(new String[]{}));
        } finally {
            keyGeneratorLogger.setLevel(originalLevel);
        }
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
