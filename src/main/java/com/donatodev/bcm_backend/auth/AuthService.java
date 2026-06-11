package com.donatodev.bcm_backend.auth;

import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.AccountNotVerifiedException;
import com.donatodev.bcm_backend.exception.AmbiguousUsernameException;
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
        return authenticate(username, password, null);
    }

    /**
     * Authenticates a user by username and password, optionally disambiguated by organization slug.
     * <p>
     * Since usernames are only unique per-organization, a username may match users in
     * more than one organization. In that case, {@code organizationSlug} must be
     * provided to identify the correct account.
     *
     * @param username the user's username
     * @param password the raw password to verify
     * @param organizationSlug optional slug of the organization the user belongs to
     * @return an {@link AuthResponseDTO} containing the access token and refresh token
     * @throws UsernameNotFoundException if the username is not found
     * @throws AmbiguousUsernameException if the username matches users in multiple organizations
     * and no {@code organizationSlug} was provided
     * @throws BadCredentialsException if the password is incorrect
     * @throws AccountNotVerifiedException if the account is not yet verified
     */
    public AuthResponseDTO authenticate(String username, String password, String organizationSlug) {
        Users user = findUser(username, organizationSlug);

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

    /**
     * Resolves the {@link Users} account matching the given username, using the
     * {@code organizationSlug} to disambiguate when the username exists in multiple
     * organizations.
     *
     * @param username the user's username
     * @param organizationSlug optional slug of the organization the user belongs to
     * @return the matching {@link Users} entity
     * @throws UsernameNotFoundException if no matching user is found
     * @throws AmbiguousUsernameException if multiple users share the username and
     * no {@code organizationSlug} was provided
     */
    @SuppressWarnings("java:S5804") // both branches throw the same generic message, mapped to an
    // identical 401 response by GlobalExceptionHandler — no enumeration is possible
    private Users findUser(String username, String organizationSlug) {
        if (organizationSlug != null && !organizationSlug.isBlank()) {
            return usersRepository.findByUsernameAndOrganizationSlug(username, organizationSlug)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
        }

        List<Users> matches = usersRepository.findAllByUsername(username);
        if (matches.isEmpty()) {
            throw new UsernameNotFoundException("Invalid username or password");
        }
        if (matches.size() > 1) {
            throw new AmbiguousUsernameException(
                    "Multiple accounts found for this username. Please specify your organization.");
        }
        return matches.get(0);
    }
}
