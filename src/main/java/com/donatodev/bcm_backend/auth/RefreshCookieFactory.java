/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.service.RefreshTokenService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Builds the HttpOnly cookie used to carry the refresh token. Centralizing
 * cookie construction here keeps every issuer (login, organization
 * registration, token refresh, logout) consistent on name, flags, and path.
 */
@Component
public class RefreshCookieFactory {

    /** Its name over plain HTTP (a laptop, the tests): the prefix below needs Secure. */
    public static final String COOKIE_NAME = "refresh_token";

    /**
     * Its name when Secure, as in production (OWASP ASVS 3.3.1): a browser
     * accepts a cookie with this prefix only over HTTPS and only if Secure, so
     * no page served over plain HTTP, on any subdomain, can plant one. __Host-
     * would also tie it to this host, but needs Path=/, and this cookie is
     * sent to /auth only. Since 2026-11-23: sessions held under the old name
     * ended once, at that deploy.
     */
    public static final String SECURE_COOKIE_NAME = "__Secure-refresh_token";

    @Value("${app.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    private final RefreshTokenService refreshTokenService;

    public RefreshCookieFactory(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Builds the cookie that carries a freshly issued refresh token.
     */
    public ResponseCookie create(String token) {
        return baseCookie(token)
                .maxAge(refreshTokenService.getRefreshExpirationMs() / 1000)
                .build();
    }

    /**
     * Builds the cookie used to delete the refresh token on logout.
     */
    public ResponseCookie clear() {
        return baseCookie("")
                .maxAge(0)
                .build();
    }

    /** The cookie's name here: {@link #SECURE_COOKIE_NAME} when Secure, {@link #COOKIE_NAME} otherwise. */
    public String name() {
        return cookieSecure ? SECURE_COOKIE_NAME : COOKIE_NAME;
    }

    /** The refresh token the request carries under this environment's name, or null. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = name();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(name(), value)
                .httpOnly(true)
                .secure(cookieSecure)
                // Strict: the browser sends it only on requests from this site; the
                // refresh and the logout are calls from the app's own pages, never
                // a navigation from elsewhere. A request from another origin, even a
                // sibling subdomain, is refused (403) by CORS before reaching them
                // (CorsConfig; AuthControllerTest.cookieEndpointsRefuseAForeignOrigin).
                .sameSite("Strict")
                .path(contextPath + "/auth");
    }
}
