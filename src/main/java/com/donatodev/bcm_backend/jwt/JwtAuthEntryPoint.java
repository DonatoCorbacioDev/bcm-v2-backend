package com.donatodev.bcm_backend.jwt;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Entry point for unauthorized requests intercepted by Spring Security.
 * <p>
 * This component is triggered whenever a request is made to a secured endpoint
 * without proper authentication credentials (e.g., missing or invalid JWT token).
 * It responds with HTTP status code {@code 401 Unauthorized}.
 */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    /**
     * Called when an unauthenticated user tries to access a secured REST endpoint.
     *
     * @param request       the HTTP request
     * @param response      the HTTP response
     * @param authException the authentication exception thrown
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException, ServletException {

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
}
