package com.donatodev.bcm_backend.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown during login when a username matches users in more than one
 * organization and no {@code organizationSlug} was provided to disambiguate.
 */
public class AmbiguousUsernameException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    public AmbiguousUsernameException(String message) {
        super(message);
    }
}
