package com.donatodev.bcm_backend.util;

import java.security.KeyPair;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Jwts;

/**
 * Utility class for generating the RSA key pair used to sign the internal
 * {@code X-Internal-Claims} JWT that MlProxyService attaches to every call
 * to the ML (FastAPI) service, asserting the caller's org_id/manager_id.
 * <p>
 * Asymmetric on purpose: the backend holds the private key and signs, the ML
 * service holds only the public key and verifies — compromising the ML
 * container's environment does not grant the ability to forge a different
 * org_id/manager_id, unlike a shared-secret (HMAC) scheme would.
 * <p>
 * Run this utility once to obtain a key pair, then set
 * {@code ml.claims.private-key} (backend) and the FastAPI service's
 * {@code INTERNAL_CLAIMS_PUBLIC_KEY} (ml) from its output.
 */
public final class InternalClaimsKeyGenerator {

    private static final Logger logger = LoggerFactory.getLogger(InternalClaimsKeyGenerator.class);
    private static final String CRLF_REGEX = "[\r\n]";

    private InternalClaimsKeyGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void main(String[] args) {
        KeyPair keyPair = Jwts.SIG.RS256.keyPair().build();

        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        logger.info("ML claims private key (PKCS8, base64) — set as ml.claims.private-key on the BACKEND only:");
        logger.info("{}", safe(privateKeyBase64));
        logger.info("ML claims public key (X.509, base64) — set as INTERNAL_CLAIMS_PUBLIC_KEY on the ML service:");
        logger.info("{}", safe(publicKeyBase64));
    }

    private static String safe(String value) {
        return value.replaceAll(CRLF_REGEX, "_");
    }
}
