package com.donatodev.bcm_backend.service;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlClaimsSignerTest {

    private final KeyPair keyPair = Jwts.SIG.RS256.keyPair().build();

    private MlClaimsSigner signerWithKey() {
        MlClaimsSigner signer = new MlClaimsSigner();
        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        ReflectionTestUtils.setField(signer, "privateKeyBase64", privateKeyBase64);
        return signer;
    }

    private Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    @DisplayName("Returns null when no private key is configured")
    void returnsNullWhenNoKeyConfigured() {
        MlClaimsSigner signer = new MlClaimsSigner();

        assertNull(signer.sign(1L, 2L));
    }

    @Test
    @DisplayName("Returns null when the private key is blank")
    void returnsNullWhenKeyBlank() {
        MlClaimsSigner signer = new MlClaimsSigner();
        ReflectionTestUtils.setField(signer, "privateKeyBase64", "   ");

        assertNull(signer.sign(1L, 2L));
    }

    @Test
    @DisplayName("Signs a token carrying orgId and managerId, verifiable with the matching public key")
    void signsTokenWithClaims() {
        MlClaimsSigner signer = signerWithKey();

        String token = signer.sign(5L, 42L);
        Claims claims = verify(token);

        assertEquals(5, ((Number) claims.get("orgId")).longValue());
        assertEquals(42, ((Number) claims.get("managerId")).longValue());
    }

    @Test
    @DisplayName("Signs a token with a null managerId (ADMIN / internal caller)")
    void signsTokenWithNullManagerId() {
        MlClaimsSigner signer = signerWithKey();

        String token = signer.sign(5L, null);
        Claims claims = verify(token);

        assertEquals(5, ((Number) claims.get("orgId")).longValue());
        assertNull(claims.get("managerId"));
    }

    @Test
    @DisplayName("Wraps a malformed private key into IllegalStateException")
    void wrapsMalformedPrivateKey() {
        MlClaimsSigner signer = new MlClaimsSigner();
        ReflectionTestUtils.setField(signer, "privateKeyBase64", "not-valid-base64-pkcs8!!");

        assertThrows(IllegalStateException.class, () -> signer.sign(1L, null));
    }

    @Test
    @DisplayName("Token expires 30 seconds after issuance")
    void tokenExpiresAfter30Seconds() {
        MlClaimsSigner signer = signerWithKey();
        Instant fixedNow = Instant.parse("2026-01-01T00:00:00Z");
        signer.setClock(Clock.fixed(fixedNow, ZoneOffset.UTC));

        String token = signer.sign(1L, null);
        // Parse with the same fixed clock — otherwise the parser's own
        // (real) expiration check would reject a token deliberately signed
        // in the past, which is the whole point of this test.
        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .clock(() -> java.util.Date.from(fixedNow))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals(fixedNow.plusSeconds(30), claims.getExpiration().toInstant());
        assertTrue(claims.getExpiration().toInstant().isAfter(claims.getIssuedAt().toInstant()));
    }
}
