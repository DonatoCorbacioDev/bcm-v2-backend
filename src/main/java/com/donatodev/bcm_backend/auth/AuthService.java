/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.AccountNotVerifiedException;
import com.donatodev.bcm_backend.exception.AmbiguousUsernameException;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Service class responsible for handling authentication logic.
 * <p>
 * Validates user credentials and generates JWT tokens upon successful login.
 */
@Service
public class AuthService {

    /**
     * RateLimitingFilter already throttles /auth/login by client IP, but that
     * does nothing against a distributed or IP-rotating attacker targeting one
     * known username - this is the per-account backstop.
     */

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionIssuer sessionIssuer;

    public AuthService(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                       SessionIssuer sessionIssuer) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionIssuer = sessionIssuer;
    }

    /**
     * Authenticates a user by username and password.
     *
     * @param username the user's username
     * @param password the raw password to verify
     * @return a {@link LoginOutcome} carrying either the issued tokens, or an
     * MFA-pending token if the account has 2FA enabled
     * @throws UsernameNotFoundException if the username is not found
     * @throws BadCredentialsException if the password is incorrect
     * @throws LockedException if the account is temporarily locked after too many failed attempts
     * @throws AccountNotVerifiedException if the account is not yet verified
     */
    public LoginOutcome authenticate(String username, String password) {
        return authenticate(username, password, null);
    }

    /**
     * Authenticates a user by username and password, optionally disambiguated by organization slug.
     * <p>
     * Since usernames are only unique per-organization, a username may match users in
     * more than one organization. In that case, {@code organizationSlug} must be
     * provided to identify the correct account.
     * <p>
     * If the account has TOTP 2FA enabled, this stops short of issuing real
     * tokens: the caller must complete the login via
     * {@code TwoFactorAuthService.verifyLogin} using the returned MFA-pending
     * token. An account that must have 2FA ({@link TwoFactorPolicy}) but has
     * not set it up gets only a setup token for the enrollment endpoints.
     *
     * @param username the user's username
     * @param password the raw password to verify
     * @param organizationSlug optional slug of the organization the user belongs to
     * @return a {@link LoginOutcome} carrying the issued tokens, an
     * MFA-pending token, or a 2FA setup token
     * @throws UsernameNotFoundException if the username is not found
     * @throws AmbiguousUsernameException if the username matches users in multiple organizations
     * and no {@code organizationSlug} was provided
     * @throws BadCredentialsException if the password is incorrect
     * @throws LockedException if the account is temporarily locked after too many failed attempts
     * @throws AccountNotVerifiedException if the account is not yet verified
     */
    public LoginOutcome authenticate(String username, String password, String organizationSlug) {
        Users user = findUser(username, organizationSlug);

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LoginLockout.ensureNotLocked(user, now);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            LoginLockout.recordFailure(user, now);
            usersRepository.save(user);
            throw new BadCredentialsException("Nome utente o password non validi");
        }

        if (LoginLockout.recordSuccess(user)) {
            usersRepository.save(user);
        }

        if (!user.isVerified()) {
            throw new AccountNotVerifiedException("Account non verificato. Controlla la tua email");
        }

        // 2FA on: the code comes next. An admin without it: set it up first.
        return sessionIssuer.afterFirstFactor(user);
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
                    .orElseThrow(() -> new UsernameNotFoundException("Nome utente o password non validi"));
        }

        List<Users> matches = usersRepository.findAllByUsername(username);
        if (matches.isEmpty()) {
            throw new UsernameNotFoundException("Nome utente o password non validi");
        }
        if (matches.size() > 1) {
            throw new AmbiguousUsernameException(
                    "Trovati più account con questo username. Specifica la tua organizzazione.");
        }
        return matches.get(0);
    }
}
