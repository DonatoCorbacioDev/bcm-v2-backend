package com.donatodev.bcm_backend.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.AccountNotVerifiedException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.RefreshTokenService;

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
    private final RefreshTokenService refreshTokenService;

    public AuthService(JwtUtils jwtUtils, UsersRepository usersRepository,
                       PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
        this.jwtUtils = jwtUtils;
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Authenticates a user by username and password.
     *
     * @param username the user's username
     * @param password the raw password to verify
     * @return an {@link AuthResponseDTO} containing the access token and refresh token
     * @throws UsernameNotFoundException if the username is not found
     * @throws BadCredentialsException if the password is incorrect
     * @throws AccountNotVerifiedException if the account is not yet verified
     */
    public AuthResponseDTO authenticate(String username, String password) {
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (!user.isVerified()) {
            throw new AccountNotVerifiedException("Account not verified. Please check your email");
        }

        String accessToken = jwtUtils.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user).getToken();
        return new AuthResponseDTO(accessToken, refreshToken);
    }
}
