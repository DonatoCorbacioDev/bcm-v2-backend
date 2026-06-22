package com.donatodev.bcm_backend.auth;

/**
 * HTTP response body for login/refresh/registration endpoints.
 * <p>
 * Only carries the short-lived access token. The refresh token never
 * appears in a JSON body: it is transported exclusively as an HttpOnly
 * cookie set by {@link RefreshCookieFactory}, so client-side JavaScript
 * (and any XSS payload) never has access to it.
 *
 * @param token the JWT access token used for authenticated requests
 */
public record AccessTokenResponse(String token) {}
