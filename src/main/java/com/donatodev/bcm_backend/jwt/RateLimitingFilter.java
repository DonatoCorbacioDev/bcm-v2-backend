/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.jwt;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.donatodev.bcm_backend.security.SecurityEvents;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/auth/login", "/auth/2fa/verify", "/auth/2fa/enroll/setup", "/auth/2fa/enroll/confirm",
            "/auth/forgot-password", "/auth/reset-password",
            "/auth/refresh", "/auth/complete-invite",
            "/organizations/register", "/organizations/register/resend",
            "/organizations/register/confirm",
            // Not a brute-force target, but each call reads the whole organization.
            "/organizations/me/export",
            // Both check a secret (password, emailed code).
            "/organizations/me/deletion", "/organizations/me/deletion/confirm");

    private static final String BUCKET_KEY_PREFIX = "rate-limit:";

    private final RateLimitBucketSource bucketSource;
    private final SecurityEvents securityEvents;

    @Value("${rate-limit.requests-per-minute:5}")
    private int requestsPerMinute;

    @Value("${rate-limit.refresh-requests-per-minute:30}")
    private int refreshRequestsPerMinute;

    public RateLimitingFilter(RateLimitBucketSource bucketSource, SecurityEvents securityEvents) {
        this.bucketSource = bucketSource;
        this.securityEvents = securityEvents;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (RATE_LIMITED_PATHS.contains(request.getServletPath())) {
            String ip = request.getRemoteAddr();

            boolean refresh = "/auth/refresh".equals(request.getServletPath());
            // The 2FA setup after a login has its own budget, so setting it up
            // (two calls, maybe a mistyped code) never uses up the login's.
            String bucket = "";
            if (refresh) {
                bucket = "refresh:";
            } else if (request.getServletPath().startsWith("/auth/2fa/enroll/")) {
                bucket = "enroll:";
            }
            String bucketKey = BUCKET_KEY_PREFIX + bucket + ip;
            int limit = refresh ? refreshRequestsPerMinute : requestsPerMinute;

            if (!bucketSource.tryConsume(bucketKey, limit)) {
                securityEvents.record(SecurityEvents.Type.RATE_LIMITED, request.getServletPath());
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"status\":429,\"message\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
