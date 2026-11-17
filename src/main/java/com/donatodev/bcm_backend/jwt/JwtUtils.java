/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.jwt;

import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.security.AuthenticatedUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;

/**
 * Utility component for generating, validating, and extracting claims from
 * JWT access tokens (JJWT 0.12.x). Nothing here logs a token or a parser
 * message, which can quote one (OWASP JWT Cheat Sheet).
 */
@Component
public class JwtUtils {

    private Clock clock = Clock.systemDefaultZone();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expirationMs}")
    private int jwtExpirationMs;

    private static final long MFA_PENDING_TOKEN_TTL_MS = 5 * 60 * 1000L;
    private static final String CLAIM_MFA_PENDING = "mfaPending";
    private static final long MFA_SETUP_TOKEN_TTL_MS = 10 * 60 * 1000L;
    private static final String CLAIM_MFA_SETUP = "mfaSetup";
    private static final String CLAIM_ORG_ID = "orgId";

    /**
     * Who issued the token and what it is for (RFC 7519 {@code iss}/{@code aud}).
     * The parser refuses any other issuer; the audience keeps a session token
     * and a two-factor step token apart even though they share the signing key,
     * so neither can be presented where the other is expected.
     */
    public static final String ISSUER = "bcm-backend";
    public static final String AUDIENCE_API = "bcm-api";
    public static final String AUDIENCE_MFA = "bcm-mfa";

    public Clock getClock() {
        return this.clock;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * The signing key before the current one, during a rotation (OWASP Secrets
     * Management Cheat Sheet): tokens it signed are still checked against it
     * until they expire, nothing is ever signed with it. Empty otherwise.
     */
    @Value("${jwt.previous-secret:}")
    private String previousJwtSecret;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey getPreviousKey() {
        if (previousJwtSecret == null || previousJwtSecret.isBlank()) {
            return null;
        }
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(previousJwtSecret));
    }

    /**
     * Generates a signed JWT for the authenticated principal, with subject set
     * to the username.
     */
    public String generateJwtToken(Authentication authentication) {
        // Spring Security 7 declares the principal nullable: fail loudly instead of an NPE.
        if (!(authentication.getPrincipal() instanceof UserDetails userPrincipal)) {
            throw new IllegalArgumentException("Authentication principal is not a UserDetails");
        }

        Instant now = clock.instant();
        Instant expiration = now.plusMillis(jwtExpirationMs);

        return baseToken(userPrincipal.getUsername(), AUDIENCE_API, now, expiration)
                .signWith(getSigningKey(), algorithm())
                .compact();
    }

    private JwtBuilder baseToken(String subject, String audience, Instant issuedAt, Instant expiration) {
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(audience).and()
                .subject(subject)
                .issuedAt(toLegacyDate(issuedAt))
                .expiration(toLegacyDate(expiration));
    }

    /**
     * Generates a signed JWT directly from a user entity, including the orgId
     * claim used for multi-tenancy.
     */
    public String generateTokenFromUser(Users user) {
        Instant now = clock.instant();
        Instant expiration = now.plusMillis(jwtExpirationMs);
        UUID orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;

        return baseToken(user.getUsername(), AUDIENCE_API, now, expiration)
                .claim(CLAIM_ORG_ID, orgId != null ? orgId.toString() : null)
                .signWith(getSigningKey(), algorithm())
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
     * Issues a short-lived token identifying a login that has passed the
     * password check but still needs a second (TOTP) factor. It carries the
     * {@code mfaPending} claim so {@link #isMfaPendingToken} — and the
     * authentication filter — can tell it apart from a real access token.
     */
    public String generateMfaPendingToken(Users user) {
        Instant now = clock.instant();
        Instant expiration = now.plusMillis(MFA_PENDING_TOKEN_TTL_MS);
        UUID orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;

        return baseToken(user.getUsername(), AUDIENCE_MFA, now, expiration)
                .claim(CLAIM_ORG_ID, orgId != null ? orgId.toString() : null)
                .claim(CLAIM_MFA_PENDING, true)
                .signWith(getSigningKey(), algorithm())
                .compact();
    }

    /**
     * Issues a short-lived token for an account that passed the password check
     * but must set two-factor authentication up first ({@code TwoFactorPolicy}).
     * It also carries {@code mfaPending}, so the authentication filter refuses
     * it like any token that is not a real session; only the enrollment
     * endpoints accept it, through {@link #isMfaSetupToken}.
     */
    public String generateMfaSetupToken(Users user) {
        Instant now = clock.instant();
        UUID orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;

        return baseToken(user.getUsername(), AUDIENCE_MFA, now, now.plusMillis(MFA_SETUP_TOKEN_TTL_MS))
                .claim(CLAIM_ORG_ID, orgId != null ? orgId.toString() : null)
                .claim(CLAIM_MFA_PENDING, true)
                .claim(CLAIM_MFA_SETUP, true)
                .signWith(getSigningKey(), algorithm())
                .compact();
    }

    /** True for a token from {@link #generateMfaSetupToken}; false for anything else, malformed or expired. */
    public boolean isMfaSetupToken(String token) {
        try {
            return hasAudience(token, AUDIENCE_MFA)
                    && Boolean.TRUE.equals(getClaimFromToken(token, claims -> claims.get(CLAIM_MFA_SETUP)));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * True if the token is a short-lived MFA-pending token rather than a
     * real access token. Returns false (not an exception) for malformed or
     * expired input.
     */
    public boolean isMfaPendingToken(String token) {
        try {
            Object claim = getClaimFromToken(token, claims -> claims.get(CLAIM_MFA_PENDING));
            return hasAudience(token, AUDIENCE_MFA) && Boolean.TRUE.equals(claim);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
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
     * Extracts the orgId custom claim, or null if the token doesn't carry a
     * valid one: tokens issued before multi-tenancy, or before ids became
     * UUIDs (a numeric orgId, ADR-0009). Null means the token authenticates
     * nobody (fail closed, see JwtAuthenticationFilter).
     */
    public UUID getOrganizationIdFromToken(String token) {
        Object orgId = getClaimFromToken(token, claims -> claims.get(CLAIM_ORG_ID));

        if (!(orgId instanceof String text)) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Validates a session token: signature, issuer, expiration, the API
     * audience (so a two-factor step token is refused here), and that its
     * subject matches the given user details. Returns false instead of
     * throwing for any malformed, expired, or invalid token.
     */
    public boolean validateJwtToken(String token, UserDetails userDetails) {
        try {
            final String username = getUsernameFromJwtToken(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token)
                    && hasAudience(token, AUDIENCE_API)
                    && !issuedBeforeCredentialsChanged(token, userDetails);
        } catch (JwtException | IllegalArgumentException e) {
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
     * Whether the token predates the user's last credential change (OWASP API2):
     * a new password or 2FA setting ends the sessions opened before it at once.
     * Compared in whole seconds, like the {@code iat} claim, so the token issued
     * right after the change, in the same second, is still accepted.
     */
    private boolean issuedBeforeCredentialsChanged(String token, UserDetails userDetails) {
        if (!(userDetails instanceof AuthenticatedUser user) || user.getCredentialsChangedAt() == null) {
            return false;
        }
        java.util.Date issuedAt = getClaimFromToken(token, Claims::getIssuedAt);
        return issuedAt == null || issuedAt.toInstant().getEpochSecond() < user.getCredentialsChangedAt();
    }

    private boolean isTokenExpired(String token) {
        return getExpirationInstantFromToken(token).isBefore(clock.instant());
    }

    /**
     * Accepts the one algorithm the backend signs with: a token naming any
     * other, even another HMAC the key would allow, is refused (OWASP JWT
     * Cheat Sheet). Unsigned tokens are refused by JJWT itself.
     */
    private static JwtParser parserFor(SecretKey key) {
        return Jwts.parser()
                .sig().clear().add(algorithmFor(key)).and()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .build();
    }

    /**
     * The HMAC the key is long enough for, the same JJWT would pick: HS384
     * for the production key. Fixed here so signing and checking agree on it.
     */
    public static MacAlgorithm algorithmFor(SecretKey key) {
        int bits = key.getEncoded().length * Byte.SIZE;
        if (bits >= 512) {
            return Jwts.SIG.HS512;
        }
        return bits >= 384 ? Jwts.SIG.HS384 : Jwts.SIG.HS256;
    }

    private MacAlgorithm algorithm() {
        return algorithmFor(getSigningKey());
    }

    /** Whether the token is meant for the given audience; a token with none is meant for nothing. */
    private boolean hasAudience(String token, String audience) {
        java.util.Set<String> audiences = getClaimFromToken(token, Claims::getAudience);
        return audiences != null && audiences.contains(audience);
    }

    /**
     * Reads a claim of a token that passed the checks, and also has an expiry
     * and an issue time: a token without either is refused, never taken as
     * valid forever or as issued after the last credential change.
     */
    private <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        Jws<Claims> jws = parseSigned(token);
        Claims claims = jws.getPayload();
        requireClaim(jws, Claims.EXPIRATION, claims.getExpiration());
        requireClaim(jws, Claims.ISSUED_AT, claims.getIssuedAt());
        return claimsResolver.apply(claims);
    }

    /**
     * Checks the token against the current key and, only when that signature
     * does not match and a rotation is under way, against the previous one.
     * Every other check (algorithm, issuer, expiry) is the same for both.
     */
    private Jws<Claims> parseSigned(String token) {
        try {
            return parserFor(getSigningKey()).parseSignedClaims(token);
        } catch (io.jsonwebtoken.security.SignatureException e) {
            SecretKey previous = getPreviousKey();
            if (previous == null) {
                throw e;
            }
            return parserFor(previous).parseSignedClaims(token);
        }
    }

    private static void requireClaim(Jws<Claims> jws, String name, Object value) {
        if (value == null) {
            throw new MissingClaimException(jws.getHeader(), jws.getPayload(), name, null,
                    "JWT without the required " + name + " claim");
        }
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
