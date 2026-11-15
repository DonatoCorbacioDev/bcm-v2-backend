/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.AccountNotVerifiedException;
import com.donatodev.bcm_backend.exception.AmbiguousUsernameException;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.security.PublicDemoGuard;

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

    /** The one answer to a wrong name, a wrong password or a locked account (OWASP JWT Cheat Sheet). */
    static final String INVALID_CREDENTIALS = "Nome utente o password non validi";

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionIssuer sessionIssuer;
    private final LoginAttemptMonitor loginAttemptMonitor;
    private final PublicDemoGuard publicDemoGuard;
    /** A hash of no one's password, checked when the name matches no account. */
    private final String dummyHash;

    public AuthService(UsersRepository usersRepository, PasswordEncoder passwordEncoder,
                       SessionIssuer sessionIssuer, LoginAttemptMonitor loginAttemptMonitor,
                       PublicDemoGuard publicDemoGuard) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionIssuer = sessionIssuer;
        this.loginAttemptMonitor = loginAttemptMonitor;
        this.publicDemoGuard = publicDemoGuard;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Authenticates a user by username and password.
     *
     * @param username the user's username
     * @param password the raw password to verify
     * @return a {@link LoginOutcome} carrying either the issued tokens, or an
     * MFA-pending token if the account has 2FA enabled
     * @throws BadCredentialsException if the name is unknown, the password is
     * wrong or the account is locked
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
     * @throws BadCredentialsException if the name matches no account, the
     * password is wrong or the account is locked: one answer for the three
     * @throws AmbiguousUsernameException if the password is right for accounts
     * in more than one organization and no {@code organizationSlug} was provided
     * @throws AccountNotVerifiedException if the account is not yet verified
     */
    public LoginOutcome authenticate(String username, String password, String organizationSlug) {
        List<Users> candidates = candidates(username, organizationSlug);
        if (candidates.isEmpty()) {
            // As long as a real check: the answer's timing must not tell the name is unknown.
            passwordEncoder.matches(password, dummyHash);
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        List<Users> matching = new ArrayList<>();
        List<Users> open = new ArrayList<>();
        for (Users candidate : candidates) {
            boolean locked = !publicDemoGuard.isPublicDemo(candidate.getUsername())
                    && LoginLockout.isLocked(candidate, now);
            // Checked even when locked, so a locked account takes as long as any other.
            boolean right = passwordEncoder.matches(password, candidate.getPasswordHash());
            if (!locked) {
                open.add(candidate);
                if (right) {
                    matching.add(candidate);
                }
            }
        }

        if (matching.isEmpty()) {
            // A locked account answers like a wrong password, and counts nothing more.
            open.forEach(candidate -> recordFailure(candidate, now));
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        // Asked only of someone who knows the password: before, it told the name exists.
        if (matching.size() > 1) {
            throw new AmbiguousUsernameException(
                    "Trovati più account con questo username. Specifica la tua organizzazione.");
        }

        Users user = matching.get(0);
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
     * Counts a wrong password against the account, and records it. The public
     * demo account, whose password is printed on the landing page, is never
     * locked: anyone could otherwise lock every visitor out of it.
     */
    private void recordFailure(Users user, LocalDateTime now) {
        loginAttemptMonitor.passwordFailed(user);
        if (publicDemoGuard.isPublicDemo(user.getUsername())) {
            return;
        }
        boolean lockedNow = LoginLockout.recordFailure(user, now);
        usersRepository.save(user);
        if (lockedNow) {
            loginAttemptMonitor.locked(user);
        }
    }

    /**
     * The accounts the name may mean: the one in the given organization, or
     * every account with that name when no organization is given.
     */
    private List<Users> candidates(String username, String organizationSlug) {
        if (organizationSlug != null && !organizationSlug.isBlank()) {
            return usersRepository.findByUsernameAndOrganizationSlug(username, organizationSlug)
                    .map(List::of).orElseGet(List::of);
        }
        return usersRepository.findAllByUsername(username);
    }
}
