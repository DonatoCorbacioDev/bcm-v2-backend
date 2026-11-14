/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.jwt;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.service.CustomUserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, CustomUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    /** The calendar feed's secret token in its path, e.g. {@code /calendar/<token>.ics}. */
    private static final Pattern CALENDAR_FEED_TOKEN = Pattern.compile("(/calendar/)[^/]+?(\\.ics)?$");

    /**
     * The request path as it goes into every log line of the request (MDC,
     * shipped as an ECS field in production). The calendar feed URL is a
     * bearer secret, the only one carried in a path, so it is masked: a log
     * reader must not be able to subscribe to someone's deadlines.
     */
    public static String loggablePath(String requestUri) {
        return requestUri == null ? null : CALENDAR_FEED_TOKEN.matcher(requestUri).replaceFirst("$1***$2");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
        MDC.put("method", request.getMethod());
        MDC.put("path", loggablePath(request.getRequestURI()));

        try {
            String authHeader = request.getHeader("Authorization");
            String token = null;
            String username = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                try {
                    username = jwtUtils.getUsernameFromToken(token);
                } catch (RuntimeException e) {
                    username = null;
                }
            }

            if (username != null && !jwtUtils.isMfaPendingToken(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    UUID orgId = jwtUtils.getOrganizationIdFromToken(token);
                    // Fails closed: every lookup is scoped by the token's
                    // organization, so a token without one authenticates nobody
                    // (OWASP API1, BOLA). Tokens always carry it since V8.
                    UserDetails userDetails = orgId == null ? null
                            : userDetailsService.loadUserByUsername(username, orgId);
                    if (userDetails != null && jwtUtils.validateToken(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        MDC.put("username", username);

                        TenantContext.set(orgId);
                    }
                } catch (RuntimeException e) {
                    // skip auth on runtime errors
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.clear();
        }
    }
}