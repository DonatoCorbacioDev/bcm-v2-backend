package com.donatodev.bcm_backend.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class CurrentUserResolverTest {

    private static final Long ORG_ID = 5L;
    private static final String USERNAME = "admin";

    @Mock
    private UsersRepository usersRepository;

    @InjectMocks
    private CurrentUserResolver resolver;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USERNAME, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("resolve: with a tenant set")
    class WithTenant {

        @Test
        @DisplayName("returns the user scoped to the current organization")
        void shouldResolveWithinTenant() {
            TenantContext.set(ORG_ID);
            Users user = Users.builder().id(1L).username(USERNAME).build();
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.of(user));

            assertSame(user, resolver.resolve());
        }

        @Test
        @DisplayName("throws when no user matches within the current organization")
        void shouldThrowWhenNotFoundWithinTenant() {
            TenantContext.set(ORG_ID);
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> resolver.resolve());
            assertEquals("Utente non trovato", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("resolve: without a tenant set")
    class WithoutTenant {

        @Test
        @DisplayName("falls back to a tenant-agnostic lookup by username")
        void shouldResolveWithoutTenant() {
            Users user = Users.builder().id(1L).username(USERNAME).build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            assertSame(user, resolver.resolve());
        }

        @Test
        @DisplayName("throws when no user matches the username")
        void shouldThrowWhenNotFoundWithoutTenant() {
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> resolver.resolve());
            assertEquals("Utente non trovato", ex.getMessage());
        }
    }
}
