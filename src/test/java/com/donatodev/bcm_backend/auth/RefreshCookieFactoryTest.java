package com.donatodev.bcm_backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

            assertEquals(RefreshCookieFactory.COOKIE_NAME, cookie.getName());
            assertEquals("the-refresh-token", cookie.getValue());
            assertTrue(cookie.isHttpOnly());
            assertTrue(cookie.isSecure());
            assertEquals("Lax", cookie.getSameSite());
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

            assertEquals(RefreshCookieFactory.COOKIE_NAME, cookie.getName());
            assertEquals("", cookie.getValue());
            assertEquals(0L, cookie.getMaxAge().getSeconds());
        }
    }
}
