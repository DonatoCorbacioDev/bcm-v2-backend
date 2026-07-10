package com.donatodev.bcm_backend.util;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import com.donatodev.bcm_backend.exception.UserNotFoundException;

class AuthenticatedUserUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getUsernameOrThrow: returns the username of an authenticated principal")
    void shouldReturnUsernameOrThrow() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, AuthorityUtils.NO_AUTHORITIES));

        assertEquals("alice", AuthenticatedUserUtils.getUsernameOrThrow());
    }

    @Test
    @DisplayName("getUsernameOrThrow: throws when there is no authentication")
    void shouldThrowWhenNoAuthentication() {
        assertThrows(UserNotFoundException.class, AuthenticatedUserUtils::getUsernameOrThrow);
    }

    @Test
    @DisplayName("getUsernameOrThrow: throws when the authentication is not authenticated")
    void shouldThrowWhenNotAuthenticated() {
        UsernamePasswordAuthenticationToken unauthenticated =
                new UsernamePasswordAuthenticationToken("alice", "password");
        unauthenticated.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);

        assertThrows(UserNotFoundException.class, AuthenticatedUserUtils::getUsernameOrThrow);
    }

    @Test
    @DisplayName("getUsernameOrThrow: throws for the anonymous principal")
    void shouldThrowForAnonymousPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThrows(UserNotFoundException.class, AuthenticatedUserUtils::getUsernameOrThrow);
    }

    @Test
    @DisplayName("getUsernameOrNull: returns null when there is no authentication")
    void shouldReturnNullWhenNoAuthentication() {
        assertNull(AuthenticatedUserUtils.getUsernameOrNull());
    }

    @Test
    @DisplayName("getUsernameOrNull: returns null when the authentication is not authenticated")
    void shouldReturnNullWhenNotAuthenticated() {
        UsernamePasswordAuthenticationToken unauthenticated =
                new UsernamePasswordAuthenticationToken("alice", "password");
        unauthenticated.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);

        assertNull(AuthenticatedUserUtils.getUsernameOrNull());
    }

    @Test
    @DisplayName("getUsernameOrNull: returns null when the principal is null")
    void shouldReturnNullWhenPrincipalIsNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(null, null, AuthorityUtils.NO_AUTHORITIES));

        assertNull(AuthenticatedUserUtils.getUsernameOrNull());
    }

    @Test
    @DisplayName("getUsernameOrNull: extracts the username from a UserDetails principal")
    void shouldExtractUsernameFromUserDetails() {
        User principal = new User("bob", "password", AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, AuthorityUtils.NO_AUTHORITIES));

        assertEquals("bob", AuthenticatedUserUtils.getUsernameOrNull());
    }

    @Test
    @DisplayName("getUsernameOrNull: falls back to toString() for a non-UserDetails principal")
    void shouldFallBackToPrincipalToString() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("carol", null, AuthorityUtils.NO_AUTHORITIES));

        assertEquals("carol", AuthenticatedUserUtils.getUsernameOrNull());
    }
}
