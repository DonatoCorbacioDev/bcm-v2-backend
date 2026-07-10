package com.donatodev.bcm_backend.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import com.donatodev.bcm_backend.exception.UserNotFoundException;

/**
 * Extracts the username of the currently authenticated principal from
 * {@link SecurityContextHolder}. Centralizes the two extraction styles used
 * across services: throw-on-missing (for endpoints that require auth) and
 * return-null (for callers that decide their own not-found handling).
 */
public final class AuthenticatedUserUtils {

    private AuthenticatedUserUtils() {
    }

    /** Returns the authenticated username, or throws if there isn't one. */
    public static String getUsernameOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UserNotFoundException("No authenticated user");
        }
        return auth.getName();
    }

    /** Returns the authenticated username, or null if there isn't one. */
    public static String getUsernameOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return null;
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal.toString();
    }
}
