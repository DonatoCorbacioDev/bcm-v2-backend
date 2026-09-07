package com.donatodev.bcm_backend.service;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;

/**
 * Signs a short-lived {@code X-Internal-Claims} JWT asserting the org_id/
 * manager_id of one call to the ML (FastAPI) service — see
 * {@link InternalClaimsKeyGenerator} for why this is asymmetric (RS256)
 * rather than a shared-secret scheme: the backend holds the private key and
 * signs, FastAPI holds only the public key and verifies, so compromising the
 * ML container's environment does not grant the ability to forge a
 * different org_id/manager_id.
 * <p>
 * The internal API key ({@code ml.internal-api-key}) is unrelated and
 * unchanged by this: it gates "is this caller allowed to reach the ML
 * service at all", while this claim binds "which org/manager this specific
 * request is for". Both checks run independently on the FastAPI side.
 */
@Component
public class MlClaimsSigner {

    private static final String CLAIM_ORG_ID = "orgId";
    private static final String CLAIM_MANAGER_ID = "managerId";
    private static final long CLAIMS_TTL_MS = 30_000L;

    @Value("${ml.claims.private-key:}")
    private String privateKeyBase64;

    private Clock clock = Clock.systemDefaultZone();

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Returns a signed claims token for the given scope, or {@code null}
     * when no private key is configured — matching the empty-key-disables
     * convention already used for {@code ml.internal-api-key} in local dev.
     * Callers must omit the {@code X-Internal-Claims} header in that case.
     */
    public String sign(Long orgId, Long managerId) {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            return null;
        }

        Instant now = clock.instant();
        return Jwts.builder()
                .claim(CLAIM_ORG_ID, orgId)
                .claim(CLAIM_MANAGER_ID, managerId)
                .issuedAt(toLegacyDate(now))
                .expiration(toLegacyDate(now.plusMillis(CLAIMS_TTL_MS)))
                .signWith(getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    private PrivateKey getPrivateKey() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid ml.claims.private-key configuration", e);
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
