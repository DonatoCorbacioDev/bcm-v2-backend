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
        Users user = usersRepository.findByUsername(username)
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
