package com.donatodev.bcm_backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.service.RefreshTokenService;

@ExtendWith(MockitoExtension.class)
class RefreshCookieFactoryTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private RefreshCookieFactory refreshCookieFactory;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: RefreshCookieFactory")
    @SuppressWarnings("unused")
    class VerifyRefreshCookieFactory {

        @Test
        @Order(1)
        @DisplayName("create() builds an HttpOnly cookie carrying the token with the configured max age")
        void shouldCreateCookieWithToken() {
            ReflectionTestUtils.setField(refreshCookieFactory, "cookieSecure", true);
            ReflectionTestUtils.setField(refreshCookieFactory, "contextPath", "/api/v1");
            when(refreshTokenService.getRefreshExpirationMs()).thenReturn(604800000L);

            ResponseCookie cookie = refreshCookieFactory.create("the-refresh-token");

            assertEquals(RefreshCookieFactory.SECURE_COOKIE_NAME, cookie.getName());
            assertEquals("the-refresh-token", cookie.getValue());
            assertTrue(cookie.isHttpOnly());
            assertTrue(cookie.isSecure());
            // Strict: sent only from this site (CSRF defense, with CORS refusing a foreign Origin).
            assertEquals("Strict", cookie.getSameSite());
            assertEquals("/api/v1/auth", cookie.getPath());
            assertEquals(604800L, cookie.getMaxAge().getSeconds());
        }

        @Test
        @Order(2)
        @DisplayName("create() honours cookieSecure=false for local dev over plain HTTP")
        void shouldRespectCookieSecureFlag() {
            ReflectionTestUtils.setField(refreshCookieFactory, "cookieSecure", false);
            ReflectionTestUtils.setField(refreshCookieFactory, "contextPath", "/api/v1");
            when(refreshTokenService.getRefreshExpirationMs()).thenReturn(604800000L);

            ResponseCookie cookie = refreshCookieFactory.create("token");

            assertFalse(cookie.isSecure());
        }

        @Test
        @Order(3)
        @DisplayName("clear() builds an empty cookie with maxAge=0 to delete it client-side")
        void shouldClearCookie() {
            ReflectionTestUtils.setField(refreshCookieFactory, "cookieSecure", true);
            ReflectionTestUtils.setField(refreshCookieFactory, "contextPath", "/api/v1");

            ResponseCookie cookie = refreshCookieFactory.clear();

            assertEquals(RefreshCookieFactory.SECURE_COOKIE_NAME, cookie.getName());
            assertEquals("", cookie.getValue());
            assertEquals(0L, cookie.getMaxAge().getSeconds());
        }

        @Test
        @Order(4)
        @DisplayName("Secure, the cookie carries the __Secure- prefix; over plain HTTP its plain name (ASVS 3.3.1)")
        void nameFollowsSecure() {
            ReflectionTestUtils.setField(refreshCookieFactory, "cookieSecure", true);
            assertEquals("__Secure-refresh_token", refreshCookieFactory.name());
            ReflectionTestUtils.setField(refreshCookieFactory, "cookieSecure", false);
            assertEquals("refresh_token", refreshCookieFactory.name());
        }

        @Test
        @Order(5)
        @DisplayName("read() takes the token from this environment's name only: a cookie under the old name is not a session")
        void readsOnlyItsOwnName() {
            ReflectionTestUtils.setField(refreshCookieFactory, "cookieSecure", true);
            org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
            assertNull(refreshCookieFactory.read(request));

            request.setCookies(new jakarta.servlet.http.Cookie("refresh_token", "old-session-value"));
            assertNull(refreshCookieFactory.read(request));

            request.setCookies(new jakarta.servlet.http.Cookie("refresh_token", "old-session-value"),
                    new jakarta.servlet.http.Cookie("__Secure-refresh_token", "current-session-value"));
            assertEquals("current-session-value", refreshCookieFactory.read(request));
        }
    }
}
