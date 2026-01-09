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

    /**
     * Test: getClock should return the current clock instance.
     */
    @Test
    @Order(9)
    @DisplayName("Should return clock instance")
    void shouldReturnClockInstance() {
        Clock clock = jwtUtils.getClock();

        assertNotNull(clock);
        // Verify it's a working clock by checking it can provide an instant
        assertNotNull(clock.instant());
    }

    /**
     * Test: validateJwtToken should catch and handle MalformedJwtException.
     */
    @Test
    @Order(10)
    @DisplayName("Should handle malformed token in validateJwtToken(String)")
    void shouldHandleMalformedTokenInValidateJwtToken() {
        // Test with various malformed tokens
        assertFalse(jwtUtils.validateJwtToken(""));
        assertFalse(jwtUtils.validateJwtToken("not.a.token"));
        assertFalse(jwtUtils.validateJwtToken("malformed"));
        assertFalse(jwtUtils.validateJwtToken("header.payload")); // Missing signature
    }

    /**
     * Test: validateJwtToken should catch and handle SignatureException.
     */
    @Test
    @Order(11)
    @DisplayName("Should handle signature validation failure in validateJwtToken(String)")
    void shouldHandleSignatureExceptionInValidateJwtToken() {
        String token = jwtUtils.generateToken(testUser);

        // Tamper with the token by appending characters
        String tamperedToken = token + "tampered";

        // This should catch SignatureException and return false
        assertFalse(jwtUtils.validateJwtToken(tamperedToken));
    }

    /**
     * Test: validateJwtToken with UserDetails should catch generic exceptions.
     */
    @Test
    @Order(12)
    @DisplayName("Should handle exception in validateJwtToken(String, UserDetails)")
    void shouldHandleExceptionInValidateJwtTokenWithUserDetails() {
        // Use a completely malformed token that will cause an exception during parsing
        String malformedToken = "not.a.valid.jwt.token.at.all";

        // This should catch the exception and return false
        assertFalse(jwtUtils.validateJwtToken(malformedToken, springUser));
    }

    /**
     * Test: getUsernameFromJwtToken should be an alias for
     * getUsernameFromToken.
     */
    @Test
    @Order(13)
    @DisplayName("getUsernameFromJwtToken should work correctly")
    void shouldExtractUsernameUsingAlternativeMethod() {
        String token = jwtUtils.generateToken(testUser);

        String username = jwtUtils.getUsernameFromJwtToken(token);

        assertEquals("john", username);
    }

    /**
     * Test: getExpirationDateFromToken should return expiration date.
     */
    @Test
    @Order(14)
    @DisplayName("Should extract expiration date from token")
    void shouldExtractExpirationDate() {
        String token = jwtUtils.generateToken(testUser);

        java.util.Date expirationDate = jwtUtils.getExpirationDateFromToken(token);

        assertNotNull(expirationDate);
        // Expiration should be in the future
        assertTrue(expirationDate.after(new java.util.Date()));
    }

    /**
     * Test: validateJwtToken(String) should return false for null or empty
     * token.
     */
    @Test
    @Order(15)
    @DisplayName("Should handle null or empty token gracefully")
    void shouldHandleNullOrEmptyToken() {
        // Empty token should return false (catches IllegalArgumentException)
        assertFalse(jwtUtils.validateJwtToken(""));

        // Malformed tokens should also return false
        assertFalse(jwtUtils.validateJwtToken("invalid"));
        assertFalse(jwtUtils.validateJwtToken("invalid.token"));
    }

    /**
     * Test: validateJwtToken(String) should handle expired token.
     */
    @Test
    @Order(16)
    @DisplayName("Should handle expired token in validateJwtToken(String)")
    void shouldHandleExpiredTokenInValidateJwtTokenString() {
        // Create a clock set in the past to generate an already-expired token
        Clock pastClock = Clock.fixed(
                Instant.now().minusSeconds(86500), // More than 24 hours ago
                ZoneId.systemDefault()
        );

        // Generate token with past clock (already expired)
        jwtUtils.setClock(pastClock);
        String expiredToken = jwtUtils.generateToken(testUser);

        // Reset clock to current time
        jwtUtils.setClock(Clock.systemDefaultZone());

        // This should catch ExpiredJwtException and return false
        assertFalse(jwtUtils.validateJwtToken(expiredToken));
    }

    /**
     * Test: validateToken should return false when username doesn't match.
     */
    @Test
    @Order(17)
    @DisplayName("Should return false when token username doesn't match UserDetails")
    void shouldReturnFalseWhenUsernameDoesNotMatch() {
        // Generate a token for testUser (username: "john")
        String token = jwtUtils.generateToken(testUser);

        // Create a different Spring Security user with a different username
        User differentUser = new User("differentUsername", "password", List.of(() -> "ROLE_ADMIN"));

        // Validate the token with a different username
        // Should return false because username doesn't match
        assertFalse(jwtUtils.validateToken(token, differentUser));
    }
}
