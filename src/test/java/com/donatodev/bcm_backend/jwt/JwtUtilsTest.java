package com.donatodev.bcm_backend.jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;

import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;

import io.jsonwebtoken.security.SignatureException;

/**
 * Unit tests for {@link JwtUtils} class.
 * <p>
 * Tests JWT token generation, validation, extraction, and edge cases including
 * token expiration and tampering scenarios. Updated for JJWT 0.12.6 API
 * compatibility.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwtUtilsTest {

    @Autowired
    private JwtUtils jwtUtils;

    private Users testUser;
    private Roles testRole;
    private User springUser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        // Create test role
        testRole = Roles.builder()
                .id(1L)
                .role("ADMIN")
                .build();

        // Create test user
        testUser = Users.builder()
                .id(1L)
                .username("john")
                .passwordHash("hashedPassword")
                .role(testRole)
                .verified(true)
                .build();

        // Create Spring Security user for validation
        springUser = new User("john", "password", List.of(() -> "ROLE_ADMIN"));
    }

    /**
     * Test: Generate and validate JWT token from Authentication.
     */
    @Test
    @Order(1)
    @DisplayName("Generate and validate JWT token from Authentication")
    void shouldGenerateAndValidateTokenFromAuthentication() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(springUser, null);

        // Use the correct method for Authentication object
        String token = jwtUtils.generateJwtToken(auth);

        assertNotNull(token);
        assertEquals("john", jwtUtils.getUsernameFromToken(token));
        assertTrue(jwtUtils.validateToken(token, springUser));
    }

    /**
     * Test: Generate and validate token from Users entity.
     */
    @Test
    @Order(2)
    @DisplayName("Generate and validate JWT token from Users entity")
    void shouldGenerateAndValidateTokenFromUserEntity() {
        String token = jwtUtils.generateToken(testUser);

        assertNotNull(token);
        assertEquals("john", jwtUtils.getUsernameFromToken(token));

        assertTrue(jwtUtils.validateToken(token, springUser));
    }

    /**
     * Test: Should throw SignatureException if the token is tampered.
     */
    @Test
    @Order(3)
    @DisplayName("Token should be invalid if tampered")
    void shouldFailValidationForTamperedToken() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(springUser, null);

        // Use the correct method for Authentication object
        String token = jwtUtils.generateJwtToken(auth);

        String tampered = token + "123";

        Throwable ex = assertThrows(SignatureException.class, () -> jwtUtils.getUsernameFromToken(tampered));
        assertNotNull(ex);
    }

    /**
     * Test: Should detect expired token using mocked clock.
     */
    @Test
    @Order(4)
    @DisplayName("Token should be invalid when expired")
    void shouldFailValidationForExpiredToken() {
        // Generate a token with current time
        String token = jwtUtils.generateToken(testUser);

        // Move clock forward beyond expiration (default is usually 86400000ms = 24h)
        Clock futureClock = Clock.fixed(
                Instant.now().plusMillis(86400001), // More than 24 hours
                ZoneId.systemDefault()
        );

        // Set the future clock to make the token appear expired
        jwtUtils.setClock(futureClock);

        // The token should now be invalid because it's "expired"
        assertFalse(jwtUtils.validateToken(token, springUser));

        // Reset clock for other tests
        jwtUtils.setClock(Clock.systemDefaultZone());
    }

    /**
     * Test: Extract username from valid token.
     */
    @Test
    @Order(5)
    @DisplayName("Should extract username from valid token")
    void shouldExtractUsernameFromValidToken() {
        String token = jwtUtils.generateToken(testUser);

        String extractedUsername = jwtUtils.getUsernameFromToken(token);

        assertEquals("john", extractedUsername);
    }

    /**
     * Test: Validate token structure without user details.
     */
    @Test
    @Order(6)
    @DisplayName("Should validate token structure")
    void shouldValidateTokenStructure() {
        String token = jwtUtils.generateToken(testUser);

        assertTrue(jwtUtils.validateJwtToken(token));

        // Invalid token should return false
        assertFalse(jwtUtils.validateJwtToken("invalid.token.here"));
    }

    /**
     * Test: generateTokenFromUser method.
     */
    @Test
    @Order(7)
    @DisplayName("Should generate token from user entity")
    void shouldGenerateTokenFromUser() {
        String token = jwtUtils.generateTokenFromUser(testUser);

        assertNotNull(token);
        assertEquals("john", jwtUtils.getUsernameFromToken(token));
    }

    /**
     * Test: Both generateToken methods should produce equivalent results.
     */
    @Test
    @Order(8)
    @DisplayName("generateToken and generateTokenFromUser should be equivalent")
    void shouldProduceEquivalentTokens() {
        // Generate tokens using both methods
        String token1 = jwtUtils.generateToken(testUser);
        String token2 = jwtUtils.generateTokenFromUser(testUser);

        // Both should be valid and extract the same username
        assertNotNull(token1);
        assertNotNull(token2);

        assertEquals(jwtUtils.getUsernameFromToken(token1),
                jwtUtils.getUsernameFromToken(token2));

        assertTrue(jwtUtils.validateToken(token1, springUser));
        assertTrue(jwtUtils.validateToken(token2, springUser));
    }
}
