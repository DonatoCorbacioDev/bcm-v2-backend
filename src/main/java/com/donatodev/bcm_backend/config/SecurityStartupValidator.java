package com.donatodev.bcm_backend.config;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Validates security-critical configuration at startup in production.
 * Fails fast rather than silently running with weak or missing secrets.
 */
@Component
@Profile("prod")
public class SecurityStartupValidator implements ApplicationRunner {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${ml.internal-api-key:}")
    private String mlInternalApiKey;

    @Value("${ml.fastapi.url:http://localhost:8000}")
    private String mlFastapiUrl;

    @Override
    public void run(ApplicationArguments args) {
        validateJwtSecret();
        validateMlApiKey();
    }

    private void validateJwtSecret() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least 256 bits (32 bytes). " +
                    "Generate a secure key with the bundled JwtKeyGenerator utility.");
        }
    }

    private void validateMlApiKey() {
        boolean mlExposed = !mlFastapiUrl.contains("localhost") && !mlFastapiUrl.contains("127.0.0.1");
        if (mlExposed && (mlInternalApiKey == null || mlInternalApiKey.isBlank())) {
            throw new IllegalStateException(
                    "ML_INTERNAL_API_KEY must be set when ML_FASTAPI_URL points to a non-local host. " +
                    "The ML service would otherwise accept unauthenticated requests.");
        }
    }
}
