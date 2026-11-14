/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refuses {@link DisabledInPublicDemo} endpoints to the public demo account.
 * <p>
 * The demo login is printed on the landing page, so whoever uses it is sharing
 * it with every other visitor. The nightly reset restores the data, but until
 * then one visitor could change the password or turn on two-factor login and
 * lock everyone else out. The account is configured by username
 * ({@code bcm.demo.locked-username}); empty means no account is locked, which
 * is the default everywhere except the production demo.
 */
@Component
public class PublicDemoGuard implements HandlerInterceptor {

    static final String MESSAGE = "nella demo pubblica questa azione è disattivata";

    private final String lockedUsername;
    private final SecurityEvents securityEvents;

    public PublicDemoGuard(@Value("${bcm.demo.locked-username:}") String lockedUsername,
            SecurityEvents securityEvents) {
        this.lockedUsername = lockedUsername == null ? "" : lockedUsername.trim();
        this.securityEvents = securityEvents;
    }

    /** Whether the given user is the public demo account. */
    public boolean isPublicDemo(String username) {
        return !lockedUsername.isEmpty() && lockedUsername.equals(username);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method
                && method.hasMethodAnnotation(DisabledInPublicDemo.class)
                && isPublicDemo(AuthenticatedUserUtils.getUsernameOrNull())) {
            securityEvents.record(SecurityEvents.Type.DEMO_BLOCKED, SecurityEvents.operation(method));
            throw new DemoBlockedException();
        }
        return true;
    }

    /** A refusal already recorded as {@code DEMO_BLOCKED}, so not again as an access denial. */
    public static final class DemoBlockedException extends AccessDeniedException {
        private static final long serialVersionUID = 1L;

        DemoBlockedException() {
            super(MESSAGE);
        }
    }
}
