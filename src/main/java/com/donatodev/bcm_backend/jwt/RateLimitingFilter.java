package com.donatodev.bcm_backend.jwt;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/auth/login", "/auth/register",
            "/auth/forgot-password", "/auth/reset-password",
            "/auth/refresh", "/auth/complete-invite",
            "/organizations/register");

    private static final String BUCKET_KEY_PREFIX = "rate-limit:";

    private final RateLimitBucketSource bucketSource;

    @Value("${rate-limit.requests-per-minute:5}")
    private int requestsPerMinute;

    public RateLimitingFilter(RateLimitBucketSource bucketSource) {
        this.bucketSource = bucketSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (RATE_LIMITED_PATHS.contains(request.getServletPath())) {
            String ip = request.getRemoteAddr();

            if (!bucketSource.tryConsume(BUCKET_KEY_PREFIX + ip, requestsPerMinute)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"status\":429,\"message\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
