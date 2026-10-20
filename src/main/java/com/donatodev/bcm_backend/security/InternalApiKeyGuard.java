/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared-secret check for {@code /internal/**} ops endpoints, which have no
 * tenant user behind them and so cannot use the JWT chain. This is the
 * second layer, not the only one: Caddy refuses {@code /api/v1/internal/*}
 * from every client, so these endpoints are only reachable from the VM
 * itself on 127.0.0.1:8090 (see bcm-v2-docker's Caddyfile).
 */
@Component
public class InternalApiKeyGuard {

    public static final String HEADER = "X-Internal-Admin-Key";

    private final byte[] expectedKey;

    public InternalApiKeyGuard(@Value("${internal.admin-api-key:}") String adminApiKey) {
        this.expectedKey = adminApiKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @throws ResponseStatusException 404 when no key is configured — the
     *         endpoints are disabled and look absent, rather than accepting
     *         an empty header
     * @throws AccessDeniedException 403 on a missing or wrong key
     */
    public void verify(String providedKey) {
        if (expectedKey.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // Constant-time compare: a plain equals() leaks how many leading
        // bytes matched through response timing.
        if (providedKey == null
                || !MessageDigest.isEqual(expectedKey, providedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Chiave interna non valida");
        }
    }
}
