package com.donatodev.bcm_backend.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    private RateLimitingFilter filter;

    @BeforeEach
    void setup() {
        filter = new RateLimitingFilter();
        ReflectionTestUtils.setField(filter, "requestsPerMinute", 2);
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        request.setRemoteAddr("192.168.1.1");
        return request;
    }

    private MockHttpServletRequest registerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/register");
        request.setServletPath("/auth/register");
        request.setRemoteAddr("192.168.1.1");
        return request;
    }

    private MockHttpServletRequest otherRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/forgot-password");
        request.setServletPath("/auth/forgot-password");
        request.setRemoteAddr("192.168.1.1");
        return request;
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: RateLimitingFilter")
    @SuppressWarnings("unused")
    class RateLimitingLogic {

        @Test
        @Order(1)
        @DisplayName("Requests within limit pass through to filter chain")
        void shouldPassThroughWithinLimit() throws Exception {
            FilterChain chain = mock(FilterChain.class);

            for (int i = 0; i < 2; i++) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(loginRequest(), response, chain);
                assertNotEquals(HttpServletResponse.SC_UNAUTHORIZED,
                        response.getStatus(), "Request " + (i + 1) + " should not be rate-limited");
            }

            verify(chain, times(2)).doFilter(any(), any());
        }

        @Test
        @Order(2)
        @DisplayName("Request exceeding limit returns 429 and does not call chain")
        void shouldBlock429WhenLimitExceeded() throws Exception {
            FilterChain chain = mock(FilterChain.class);

            // Exhaust the 2-token bucket
            for (int i = 0; i < 2; i++) {
                filter.doFilter(loginRequest(), new MockHttpServletResponse(), chain);
            }

            MockHttpServletResponse blocked = new MockHttpServletResponse();
            filter.doFilter(loginRequest(), blocked, chain);

            assertEquals(429, blocked.getStatus());
            verify(chain, times(2)).doFilter(any(), any()); // chain NOT called for 3rd
        }

        @Test
        @Order(3)
        @DisplayName("/auth/register is also rate-limited")
        void shouldRateLimitRegister() throws Exception {
            FilterChain chain = mock(FilterChain.class);

            for (int i = 0; i < 2; i++) {
                filter.doFilter(registerRequest(), new MockHttpServletResponse(), chain);
            }

            MockHttpServletResponse blocked = new MockHttpServletResponse();
            filter.doFilter(registerRequest(), blocked, chain);

            assertEquals(429, blocked.getStatus());
        }

        @Test
        @Order(4)
        @DisplayName("Other endpoints bypass rate limiting entirely")
        void shouldNotRateLimitOtherPaths() throws Exception {
            FilterChain chain = mock(FilterChain.class);

            for (int i = 0; i < 10; i++) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(otherRequest(), response, chain);
                assertNotEquals(429, response.getStatus());
            }

            verify(chain, times(10)).doFilter(any(), any());
        }

        @Test
        @Order(5)
        @DisplayName("Different IPs have independent buckets")
        void shouldTrackBucketsPerIp() throws Exception {
            FilterChain chain = mock(FilterChain.class);

            MockHttpServletRequest ip1 = loginRequest();
            ip1.setRemoteAddr("10.0.0.1");

            MockHttpServletRequest ip2 = loginRequest();
            ip2.setRemoteAddr("10.0.0.2");

            // Exhaust ip1 bucket
            for (int i = 0; i < 2; i++) {
                filter.doFilter(ip1, new MockHttpServletResponse(), chain);
            }
            MockHttpServletResponse blockedIp1 = new MockHttpServletResponse();
            filter.doFilter(ip1, blockedIp1, chain);
            assertEquals(429, blockedIp1.getStatus());

            // ip2 bucket is independent — should pass
            MockHttpServletResponse ip2response = new MockHttpServletResponse();
            filter.doFilter(ip2, ip2response, chain);
            assertNotEquals(429, ip2response.getStatus());
        }
    }
}
