/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import com.donatodev.bcm_backend.exception.TooManyRequestsException;
import com.donatodev.bcm_backend.jwt.RateLimitBucketSource;

/**
 * The budget of changes per user (docs/BUSINESS_RULES.md): counted on writes
 * only, per account, refused with a 429 and a security event once used up.
 */
class WriteBudgetLimiterTest {

    private static final int BUDGET = 3;

    /** A bucket per key, refilled never: enough to see the budget run out. */
    private final Map<String, Integer> used = new HashMap<>();
    private final RateLimitBucketSource buckets = (key, perMinute) -> used.merge(key, 1, Integer::sum) <= perMinute;
    private final SecurityEvents events = mock(SecurityEvents.class);
    private final WriteBudgetLimiter limiter = new WriteBudgetLimiter(buckets, events, BUDGET);
    private final HandlerMethod handler = handler();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a user's writes past the budget are refused with a 429 and recorded")
    void budgetRunsOut() {
        signIn("anna");
        for (int i = 0; i < BUDGET; i++) {
            assertThat(limiter.preHandle(request("POST"), new MockHttpServletResponse(), handler)).isTrue();
        }
        assertThatThrownBy(() -> limiter.preHandle(request("DELETE"), new MockHttpServletResponse(), handler))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage(WriteBudgetLimiter.MESSAGE);
        verify(events).record(eq(SecurityEvents.Type.WRITE_LIMITED), anyString());
    }

    @Test
    @DisplayName("each user has a budget of their own")
    void perUser() {
        signIn("anna");
        for (int i = 0; i < BUDGET; i++) {
            limiter.preHandle(request("PUT"), new MockHttpServletResponse(), handler);
        }
        signIn("bruno");
        assertThat(limiter.preHandle(request("PUT"), new MockHttpServletResponse(), handler)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    @DisplayName("reading is never counted")
    void readsAreFree(String method) {
        signIn("anna");
        for (int i = 0; i < BUDGET * 3; i++) {
            assertThat(limiter.preHandle(request(method), new MockHttpServletResponse(), handler)).isTrue();
        }
        assertThat(used).isEmpty();
    }

    @Test
    @DisplayName("requests without an account (sign-in, registration) are left to their own limits")
    void anonymousIsNotCounted() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        for (int i = 0; i < BUDGET * 3; i++) {
            assertThat(limiter.preHandle(request("POST"), new MockHttpServletResponse(), handler)).isTrue();
        }
        SecurityContextHolder.clearContext();
        assertThat(limiter.preHandle(request("POST"), new MockHttpServletResponse(), handler)).isTrue();
        assertThat(used).isEmpty();
        verify(events, never()).record(any(), anyString());
    }

    @Test
    @DisplayName("a request that reaches no controller method is not counted")
    void notAHandlerMethod() {
        signIn("anna");
        RateLimitBucketSource counting = mock(RateLimitBucketSource.class);
        new WriteBudgetLimiter(counting, events, BUDGET).preHandle(request("POST"), new MockHttpServletResponse(),
                new Object());
        verify(counting, never()).tryConsume(anyString(), anyInt());
    }

    private static void signIn(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
    }

    private static MockHttpServletRequest request(String method) {
        return new MockHttpServletRequest(method, "/counterparties");
    }

    private static HandlerMethod handler() {
        try {
            return new HandlerMethod(new Object(), Object.class.getMethod("toString"));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
