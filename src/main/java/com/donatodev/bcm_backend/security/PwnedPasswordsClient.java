/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Whether a password is among those already leaked (OWASP ASVS 6.2.12), asked
 * of Have I Been Pwned's Pwned Passwords range API by k-anonymity: only the
 * first 5 hex characters of the password's SHA-1 leave the backend, never the
 * password, its whole hash or anything about the account; the answer lists
 * every leaked hash with that prefix, padded so its length says nothing either,
 * and the match is made here. SHA-1 because that is the API's index, not to
 * protect anything: passwords are stored as BCrypt hashes.
 * <p>
 * Fails open: when the service is off (tests, a laptop without network) or
 * does not answer within two seconds, the answer is "unknown" and the password
 * is judged by the local rules alone; setting a password never waits on, nor
 * breaks because of, a third party. The address is fixed here, not taken from
 * any request, and redirects are not followed (OWASP API7).
 */
@Component
public class PwnedPasswordsClient {

    private static final Logger logger = LoggerFactory.getLogger(PwnedPasswordsClient.class);

    static final String RANGE_URL = "https://api.pwnedpasswords.com/range/";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final boolean enabled;
    private final RestClient client;

    public PwnedPasswordsClient(@Value("${security.password.breach-check.enabled:true}") boolean enabled) {
        this.enabled = enabled;
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Add-Padding", "true")
                .defaultHeader("User-Agent", "bcm-backend-password-check")
                .build();
    }

    /** True if leaked, false if not, empty if it could not be told (off, unreachable, unexpected answer). */
    public Optional<Boolean> isBreached(String password) {
        if (!enabled || password == null) {
            return Optional.empty();
        }
        String hash = sha1Hex(password);
        String prefix = hash.substring(0, 5);
        String suffix = hash.substring(5);
        try {
            String body = client.get().uri(RANGE_URL + prefix).retrieve().body(String.class);
            if (body == null) {
                return Optional.empty();
            }
            return Optional.of(listsSuffix(body, suffix));
        } catch (RestClientException e) {
            logger.warn("Breached-password check unavailable, local rules only: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Whether the range answer lists the suffix with a count above zero (padding entries have 0). */
    static boolean listsSuffix(String body, String suffix) {
        for (String line : body.split("\r?\n")) {
            int colon = line.indexOf(':');
            // Both upper-case hex: the API answers that way, sha1Hex produces it.
            if (colon > 0 && line.substring(0, colon).trim().equals(suffix)) {
                String count = line.substring(colon + 1).trim();
                return !count.isEmpty() && !"0".equals(count);
            }
        }
        return false;
    }

    static String sha1Hex(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }
}
