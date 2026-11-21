/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.jwt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.donatodev.bcm_backend.security.SecurityEvents;
import com.donatodev.bcm_backend.service.CustomUserDetailsService;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;

/**
 * Which unreadable session tokens count as a security event: one that is not
 * ours does; one that merely expired, as every session's does every few
 * minutes, does not. Either way the request goes on, unauthenticated.
 */
class JwtInvalidTokenEventTest {

    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final SecurityEvents events = mock(SecurityEvents.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtUtils, mock(CustomUserDetailsService.class), events);

    @Test
    @DisplayName("a token with a wrong signature is recorded, by kind of failure")
    void wrongSignature() throws Exception {
        when(jwtUtils.getUsernameFromToken("not-ours")).thenThrow(new SignatureException("signature mismatch"));

        MockFilterChain chain = run("not-ours");

        verify(events).record(eq(SecurityEvents.Type.INVALID_TOKEN), contains("GET /auth/me | SignatureException"));
        org.assertj.core.api.Assertions.assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("a malformed token is recorded too")
    void malformed() throws Exception {
        when(jwtUtils.getUsernameFromToken("garbage")).thenThrow(new MalformedJwtException("bad"));

        run("garbage");

        verify(events).record(eq(SecurityEvents.Type.INVALID_TOKEN), contains("MalformedJwtException"));
    }

    @Test
    @DisplayName("an expired token is not an event")
    void expired() throws Exception {
        when(jwtUtils.getUsernameFromToken("old")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        MockFilterChain chain = run("old");

        verify(events, never()).record(any(), any());
        org.assertj.core.api.Assertions.assertThat(chain.getRequest()).isNotNull();
    }

    private MockFilterChain run(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }
}
