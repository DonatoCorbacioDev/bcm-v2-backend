package com.donatodev.bcm_backend.auth;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.service.CurrentUserResolver;
import com.donatodev.bcm_backend.service.RefreshTokenService;

/**
 * Keeps the signed-in user in after they change their own credentials (own
 * password, own 2FA): {@link com.donatodev.bcm_backend.service.SessionRevocationService}
 * has just ended every session, this one included, so a new refresh token is
 * issued for it. The current access token is refused from now on; the
 * frontend's next request gets a 401, renews through this cookie and retries,
 * while every other session stays ended.
 */
@Component
public class CurrentSessionRenewer {

    private final CurrentUserResolver currentUserResolver;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieFactory refreshCookieFactory;

    public CurrentSessionRenewer(CurrentUserResolver currentUserResolver,
                                 RefreshTokenService refreshTokenService,
                                 RefreshCookieFactory refreshCookieFactory) {
        this.currentUserResolver = currentUserResolver;
        this.refreshTokenService = refreshTokenService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    /** The Set-Cookie header value carrying a new refresh token for the current user. */
    public String renewedRefreshCookie() {
        String refreshToken = refreshTokenService.createRefreshToken(currentUserResolver.resolve());
        return refreshCookieFactory.create(refreshToken).toString();
    }
}
