package com.donatodev.bcm_backend.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * The signed-in user as Spring Security sees it, plus when their credentials
 * last changed: the JWT check refuses an access token issued before that
 * (see {@link com.donatodev.bcm_backend.jwt.JwtUtils#validateJwtToken(String,
 * org.springframework.security.core.userdetails.UserDetails)}).
 */
public class AuthenticatedUser extends User {

    private static final long serialVersionUID = 1L;

    /** Epoch seconds, or null when no change was ever recorded. */
    private final Long credentialsChangedAt;

    /**
     * The account's id, which never changes: an access token is accepted only
     * for the account it was issued to, not for whoever holds its username now
     * (a renamed or deleted user's name given to another account).
     */
    private final UUID userId;

    public AuthenticatedUser(UUID userId, String username, String passwordHash,
                             Collection<? extends GrantedAuthority> authorities, Long credentialsChangedAt) {
        super(username, passwordHash, authorities);
        this.userId = userId;
        this.credentialsChangedAt = credentialsChangedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getCredentialsChangedAt() {
        return credentialsChangedAt;
    }

    // Same identity as User (the username): the extra field does not change who this is.
    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
