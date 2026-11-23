/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.donatodev.bcm_backend.exception.TooManyRequestsException;
import com.donatodev.bcm_backend.jwt.RateLimitBucketSource;
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-user budget on every request that changes something (OWASP ASVS 2.4.1,
 * anti-automation): a script with a stolen or a curious account could
 * otherwise fill the database with counterparties, notes or areas, which no
 * other limit counts. Sixty a minute (docs/BUSINESS_RULES.md) is far more than
 * anyone filling in forms; a CSV import is one request. Keyed by the
 * authenticated username, like {@link CostlyOperationLimiter}, and in the same
 * Redis-backed buckets; requests without an account (sign-in, registration,
 * Stripe's webhook) have their own limits and are not counted here.
 */
@Component
public class WriteBudgetLimiter implements HandlerInterceptor {

    static final String MESSAGE = "Troppe modifiche in poco tempo: riprova tra un minuto";
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String ANONYMOUS = "anonymousUser";

    private final RateLimitBucketSource buckets;
    private final SecurityEvents securityEvents;
    private final int writesPerMinute;

    public WriteBudgetLimiter(RateLimitBucketSource buckets, SecurityEvents securityEvents,
            @Value("${rate-limit.writes-per-minute:60}") int writesPerMinute) {
        this.buckets = buckets;
        this.securityEvents = securityEvents;
        this.writesPerMinute = writesPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !WRITES.contains(request.getMethod())) {
            return true;
        }
        String username = AuthenticatedUserUtils.getUsernameOrNull();
        if (username == null || ANONYMOUS.equals(username)) {
            return true;
        }
        if (!buckets.tryConsume("rate-limit:write:" + username, writesPerMinute)) {
            securityEvents.record(SecurityEvents.Type.WRITE_LIMITED, SecurityEvents.operation(method));
            throw new TooManyRequestsException(MESSAGE);
        }
        return true;
    }
}
