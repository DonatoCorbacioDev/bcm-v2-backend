package com.donatodev.bcm_backend.service;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Unit tests for {@link CustomUserDetailsService}.
 * <p>
 * Verifies correct behavior when loading users by username
 * and handles cases where the user does not exist.
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("test")
class CustomUserDetailsServiceTest {

    @Mock
    private UsersRepository usersRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    /**
     * Test successful loading of a user by username.
     * Verifies username, password, and role-based authority.
     */
    @Test
    @Order(1)
    @DisplayName("Valid user upload")
    void shouldLoadUserByUsernameSuccessfully() {
        Users user = Users.builder()
                .username("user1")
                .passwordHash("password123")
                .role(Roles.builder().role("ADMIN").build())
                .build();

        when(usersRepository.findByUsername("user1")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("user1");

        assertNotNull(result);
        assertEquals("user1", result.getUsername());
        assertEquals("password123", result.getPassword());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    /**
     * Test should throw UsernameNotFoundException if the user does not exist.
     */
    @Test
    @Order(2)
    @DisplayName("Exception when user does not exist")
    void shouldThrowIfUserNotFound() {
        when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        UsernameNotFoundException ex =
                assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername("ghost"));
        assertEquals("User not found: ghost", ex.getMessage());
    }
}