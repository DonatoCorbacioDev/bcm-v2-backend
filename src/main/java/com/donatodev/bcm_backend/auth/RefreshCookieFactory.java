package com.donatodev.bcm_backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.service.RefreshTokenService;

/**
 * Builds the HttpOnly cookie used to carry the refresh token. Centralizing
 * cookie construction here keeps every issuer (login, organization
 * registration, token refresh, logout) consistent on name, flags, and path.
 */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";

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

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(contextPath + "/auth");
    }
}
