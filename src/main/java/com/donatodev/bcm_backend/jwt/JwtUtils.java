package com.donatodev.bcm_backend.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
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
 * Utility class for generating, validating, and extracting information from JWT tokens.
 * <p>
 * Updated for JJWT 0.12.6 API compatibility with enhanced security features.
 * 
 * @author Donato Corbacio
 * @version 2.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class JwtUtils {
	
	private Clock clock = Clock.systemDefaultZone();
	
	public Clock getClock() {
	    return this.clock;
	}

	public void setClock(Clock clock) {
	    this.clock = clock;
	}

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expirationMs}")
    private int jwtExpirationMs;

    /**
     * Converts the secret key into a {@link SecretKey} object used for signing JWTs.
     * <p>
     * Uses HMAC-SHA256 algorithm for enhanced security.
     *
     * @return the signing key for JWT operations
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a JWT token based on the authenticated user's details.
     * <p>
     * The token includes the username as subject and has a configurable expiration time.
     *
     * @param authentication the current authentication object containing user details
     * @return a signed JWT token string
     */
    public String generateJwtToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        
        Instant now = clock.instant();
        Instant expiration = now.plusMillis(jwtExpirationMs);
        
        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Alternative method to generate JWT token from a User entity.
     * <p>
     * Useful when you have direct access to the User entity without Authentication object.
     *
     * @param user the user entity
     * @return a signed JWT token string
     */
    public String generateTokenFromUser(Users user) {
        Instant now = clock.instant();
        Instant expiration = now.plusMillis(jwtExpirationMs);
        
        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generates a JWT token from a User entity.
     * <p>
     * Alias method for compatibility with existing code.
     *
     * @param user the user entity
     * @return a signed JWT token string
     */
    public String generateToken(Users user) {
        return generateTokenFromUser(user);
    }

    /**
     * Extracts the username from a JWT token.
     *
     * @param token the JWT token
     * @return the username (subject) from the token
     */
    public String getUsernameFromJwtToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * Extracts the username from a JWT token.
     * <p>
     * Alias method for compatibility with existing code.
     *
     * @param token the JWT token
     * @return the username (subject) from the token
     */
    public String getUsernameFromToken(String token) {
        return getUsernameFromJwtToken(token);
    }

    /**
     * Gets the expiration date from a JWT token.
     *
     * @param token the JWT token
     * @return the expiration date
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * Validates a JWT token against the provided user details.
     * <p>
     * Checks both username match and token expiration.
     *
     * @param token       the JWT token to validate
     * @param userDetails the user details to validate against
     * @return true if the token is valid for the user, false otherwise
     */
    public boolean validateJwtToken(String token, UserDetails userDetails) {
        try {
            final String username = getUsernameFromJwtToken(token);
            return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates a JWT token against the provided user details.
     * <p>
     * Alias method for compatibility with existing code.
     *
     * @param token       the JWT token to validate
     * @param userDetails the user details to validate against
     * @return true if the token is valid for the user, false otherwise
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        return validateJwtToken(token, userDetails);
    }

    /**
     * Validates a JWT token and returns true if it's valid.
     * <p>
     * This method only checks the token structure and expiration, not against specific user details.
     *
     * @param authToken the JWT token to validate
     * @return true if the token is structurally valid and not expired
     */
    public boolean validateJwtToken(String authToken) {
        try {
            getJwtParser().parseSignedClaims(authToken);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (SignatureException e) {
            log.error("JWT signature validation failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Checks if a JWT token is expired.
     *
     * @param token the JWT token
     * @return true if the token is expired, false otherwise
     */
    private boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(Date.from(clock.instant()));
    }

    /**
     * Creates and configures a JWT parser with the signing key.
     * <p>
     * Updated for JJWT 0.12.6 API - uses parser() instead of deprecated parserBuilder().
     *
     * @return configured JWT parser
     */
    private JwtParser getJwtParser() {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build();
    }

    /**
     * Extracts a specific claim from the JWT token.
     * <p>
     * Updated for JJWT 0.12.6 API compatibility.
     *
     * @param token           the JWT token
     * @param claimsResolver  function to resolve the desired claim
     * @param <T>             the claim type
     * @return the extracted claim
     */
    private <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getJwtParser()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
}