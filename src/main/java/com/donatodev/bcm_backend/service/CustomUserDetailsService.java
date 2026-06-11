package com.donatodev.bcm_backend.service;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Implementation of Spring Security's {@link UserDetailsService} that loads user-specific data.
 * <p>
 * This service loads user details from the database by username,
 * converts roles to Spring Security authorities,
 * and returns a {@link UserDetails} object for authentication.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final UsersRepository usersRepository;

    public CustomUserDetailsService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    /**
     * Locates the user based on the username.
     *
     * @param username the username identifying the user whose data is required.
     * @return a fully populated {@link UserDetails} object (never {@code null})
     * @throws UsernameNotFoundException if the user could not be found or the user has no GrantedAuthority
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return loadUserByUsername(username, null);
    }

    /**
     * Locates the user based on the username, scoped to the given organization when provided.
     * <p>
     * Since usernames are only unique per-organization, callers that know the
     * organization (e.g. the JWT authentication filter, from the token's {@code orgId}
     * claim) should pass it to avoid ambiguity when the same username exists in
     * multiple organizations.
     *
     * @param username the username identifying the user whose data is required
     * @param organizationId the ID of the organization the user belongs to, or {@code null} to look up globally
     * @return a fully populated {@link UserDetails} object (never {@code null})
     * @throws UsernameNotFoundException if the user could not be found or the user has no GrantedAuthority
     */
    public UserDetails loadUserByUsername(String username, Long organizationId) throws UsernameNotFoundException {
        Users user = (organizationId != null)
                ? usersRepository.findByUsernameAndOrganizationId(username, organizationId)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username))
                : usersRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Converts the role string to Spring Security authority format, e.g., "ADMIN" -> "ROLE_ADMIN"
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().getRole());

        return new User(
                user.getUsername(),
                user.getPasswordHash(),
                Collections.singletonList(authority)
        );
    }
}
