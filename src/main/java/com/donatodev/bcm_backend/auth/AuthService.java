package com.donatodev.bcm_backend.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.AccountNotVerifiedException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Service class responsible for handling authentication logic.
 * <p>
 * Validates user credentials and generates JWT tokens upon successful login.
 */
@Service
public class AuthService {

    private final JwtUtils jwtUtils;
    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    
    public AuthService(JwtUtils jwtUtils, UsersRepository usersRepository, PasswordEncoder passwordEncoder) {
        this.jwtUtils = jwtUtils;
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticates a user by username and password, and returns a JWT token if valid.
     *
     * @param username the user's username (typically an email)
     * @param password the raw password to verify
     * @return a JWT token if authentication is successful
     * @throws UsernameNotFoundException if the username is not found
     * @throws BadCredentialsException if the password is incorrect
     * @throws RuntimeException if the account is not verified
     */
    public String authenticate(String username, String password) {
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (!user.isVerified()) {
        	throw new AccountNotVerifiedException("Account not verified. Please check your email");
        }

        // Generate JWT token for the authenticated user
        return jwtUtils.generateToken(user);
    }
}
