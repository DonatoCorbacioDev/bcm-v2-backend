/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

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
 * Per-user budget for {@link CostlyOperation} endpoints (OWASP API4). Keyed by
 * the authenticated username, not the IP: a logged-in user switching networks
 * keeps the same budget, and colleagues behind one office IP don't share it.
 * Same Redis-backed buckets as the login limiter.
 */
@Component
public class CostlyOperationLimiter implements HandlerInterceptor {

    static final String MESSAGE = "Troppe richieste per questa operazione: riprova tra un minuto";

    private final RateLimitBucketSource buckets;
    private final int llmPerMinute;
    private final int heavyPerMinute;

    public CostlyOperationLimiter(RateLimitBucketSource buckets,
            @Value("${rate-limit.llm-per-minute:5}") int llmPerMinute,
            @Value("${rate-limit.heavy-per-minute:20}") int heavyPerMinute) {
        this.buckets = buckets;
        this.llmPerMinute = llmPerMinute;
        this.heavyPerMinute = heavyPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        CostlyOperation costly = method.getMethodAnnotation(CostlyOperation.class);
        String username = AuthenticatedUserUtils.getUsernameOrNull();
        // Every costly endpoint requires a login: without one, Spring Security answers first.
        if (costly == null || username == null) {
            return true;
        }
        boolean llm = costly.value() == CostlyOperation.Cost.LLM;
        String key = "rate-limit:cost:" + (llm ? "llm:" : "heavy:") + username;
        if (!buckets.tryConsume(key, llm ? llmPerMinute : heavyPerMinute)) {
            throw new TooManyRequestsException(MESSAGE);
        }
        return true;
    }
}
