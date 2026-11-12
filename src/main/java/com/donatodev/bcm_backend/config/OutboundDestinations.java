/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The allowlist of hosts the backend itself sends requests to (OWASP API7).
 * Only the ML service and Ollama are reached by a URL from configuration
 * (Stripe, SMTP and the like have theirs fixed by their libraries), and both
 * run next to the backend: a value pointing anywhere else is a mistake or a
 * tampered environment, so the application refuses to start with it.
 * <p>
 * No URL a user sends is ever requested by the server; {@code
 * OutboundRequestsTest} keeps it that way.
 */
@Component
public class OutboundDestinations {

    private static final Set<String> SCHEMES = Set.of("http", "https");

    private final Set<String> allowedHosts;

    public OutboundDestinations(
            @Value("${outbound.allowed-hosts}") String allowedHosts,
            @Value("${ml.fastapi.url}") String mlUrl,
            @Value("${spring.ai.ollama.base-url}") String ollamaUrl) {
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        requireAllowed("ML_FASTAPI_URL", mlUrl);
        requireAllowed("OLLAMA_URL", ollamaUrl);
    }

    /** Throws unless the URL is plain http(s), without credentials, to an allowed host. */
    void requireAllowed(String setting, String url) {
        URI uri;
        try {
            uri = new URI(url == null ? "" : url.trim());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(setting + " is not a valid URL", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!SCHEMES.contains(scheme) || uri.getUserInfo() != null || !allowedHosts.contains(host)) {
            throw new IllegalStateException(setting + " must be an http(s) URL to one of " + allowedHosts
                    + " (outbound.allowed-hosts); got host '" + host + "'");
        }
    }
}
