/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Where a request came from, kept as evidence of a Terms acceptance. The IP
 * is the real client one only because {@code server.forward-headers-strategy}
 * makes Tomcat read the X-Forwarded-For header set by the reverse proxy.
 *
 * @param ip        client IP address (IPv4 or IPv6, at most 45 characters)
 * @param userAgent browser User-Agent, truncated to fit its column
 */
public record RequestOrigin(String ip, String userAgent) {

    static final int MAX_USER_AGENT_LENGTH = 255;

    public static RequestOrigin from(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return new RequestOrigin(request.getRemoteAddr(), userAgent);
    }
}
