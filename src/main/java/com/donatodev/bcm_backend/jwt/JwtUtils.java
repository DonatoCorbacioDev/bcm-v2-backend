package com.donatodev.bcm_backend.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.entity.Users;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility component for generating, validating, and extracting claims from
 * JWT access tokens (JJWT 0.12.x).
 */
@Component
@Slf4j
public class JwtUtils {

    private static final String CRLF_REGEX = "[\r\n]";

    private Clock clock = Clock.systemDefaultZone();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expirationMs}")
    private int jwtExpirationMs;

    public Clock getClock() {
        return this.clock;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a signed JWT for the authenticated principal, with subject set
     * to the username.
     */
    public String generateJwtToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        Instant now = clock.instant();
        Instant expiration = now.plusMillis(jwtExpirationMs);

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(toLegacyDate(now))
                .expiration(toLegacyDate(expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generates a signed JWT directly from a user entity, including the orgId
     * claim used for multi-tenancy.
     */
    public String generateTokenFromUser(Users user) {
        Instant now = clock.instant();
        Instant expiration = now.plusMillis(jwtExpirationMs);
        Long orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;

        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(toLegacyDate(now))
                .expiration(toLegacyDate(expiration))
                .claim("orgId", orgId)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Alias for generateTokenFromUser, kept for backward compatibility.
     */
    public String generateToken(Users user) {
        return generateTokenFromUser(user);
    }

    public String getUsernameFromJwtToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * Alias for getUsernameFromJwtToken, kept for backward compatibility.
     */
    public String getUsernameFromToken(String token) {
        return getUsernameFromJwtToken(token);
    }

    public Instant getExpirationInstantFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration).toInstant();
    }

    /**
     * Extracts the orgId custom claim, or null if the token doesn't carry one
     * (e.g. tokens issued before multi-tenancy was introduced).
     */
    public Long getOrganizationIdFromToken(String token) {
        Object orgId = getClaimFromToken(token, claims -> claims.get("orgId"));

        if (orgId == null) {
            return null;
        }

        return ((Number) orgId).longValue();
    }

    /**
     * Validates the token's signature and expiration, and that its subject
     * matches the given user details. Returns false instead of throwing for
     * any malformed, expired, or invalid token.
     */
    public boolean validateJwtToken(String token, UserDetails userDetails) {
        try {
            final String username = getUsernameFromJwtToken(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (ExpiredJwtException | MalformedJwtException | UnsupportedJwtException
                 | IllegalArgumentException | SignatureException e) {
            return false;
        }
    }

    /**
     * Alias for validateJwtToken(String, UserDetails), kept for backward
     * compatibility.
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        return validateJwtToken(token, userDetails);
    }

    /**
     * Structural validation only (signature and expiration, no username
     * comparison). Logs and returns false on any parsing failure instead of
     * throwing.
     */
    public boolean validateJwtToken(String authToken) {
        try {
            getJwtParser().parseSignedClaims(authToken);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", sanitizeLogMessage(e));
        } catch (MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", sanitizeLogMessage(e));
        } catch (SignatureException e) {
            log.error("JWT signature validation failed: {}", sanitizeLogMessage(e));
        }

        return false;
    }

    private boolean isTokenExpired(String token) {
        return getExpirationInstantFromToken(token).isBefore(clock.instant());
    }

    private JwtParser getJwtParser() {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build();
    }

    private <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getJwtParser()
                .parseSignedClaims(token)
                .getPayload();

        return claimsResolver.apply(claims);
    }

    private String sanitizeLogMessage(Exception e) {
        return e.getMessage().replaceAll(CRLF_REGEX, "_");
    }

    /**
     * JJWT 0.12.x requires java.util.Date for standard JWT date claims.
     * The application uses java.time.Instant internally and converts only at
     * the library boundary.
     */
    @SuppressWarnings("java:S2143")
    private java.util.Date toLegacyDate(Instant instant) {
        return java.util.Date.from(instant);
    }
}
