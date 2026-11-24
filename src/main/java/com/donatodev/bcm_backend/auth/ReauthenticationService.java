/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ReauthenticationRequiredException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.CurrentUserResolver;
import com.donatodev.bcm_backend.service.TwoFactorAuthService;
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;

/**
 * A fresh proof of identity before the operations a stolen session would aim
 * at (OWASP ASVS 7.5.1): changing the email a password reset goes to, setting
 * another user's password, exporting the whole organization. The user enters
 * their password again, and their 2FA code if they use one
 * ({@link #confirm}); the answer is a token valid five minutes, sent back in
 * {@value #HEADER} with the operation ({@link #require}). A wrong password
 * counts towards the same lock as the login, and is recorded the same way.
 * Documented in docs/BUSINESS_RULES.md.
 */
@Service
public class ReauthenticationService {

    public static final String HEADER = "X-Reauth-Token";
    static final String REQUIRED = "Per questa operazione conferma la tua password";
    static final String WRONG = "Password o codice non corretti";

    private final CurrentUserResolver currentUserResolver;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorAuthService twoFactorAuthService;
    private final UsersRepository usersRepository;
    private final LoginAttemptMonitor loginAttemptMonitor;
    private final JwtUtils jwtUtils;

    public ReauthenticationService(CurrentUserResolver currentUserResolver, PasswordEncoder passwordEncoder,
            TwoFactorAuthService twoFactorAuthService, UsersRepository usersRepository,
            LoginAttemptMonitor loginAttemptMonitor, JwtUtils jwtUtils) {
        this.currentUserResolver = currentUserResolver;
        this.passwordEncoder = passwordEncoder;
        this.twoFactorAuthService = twoFactorAuthService;
        this.usersRepository = usersRepository;
        this.loginAttemptMonitor = loginAttemptMonitor;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Checks the signed-in user's password, and 2FA code if they use one, and
     * issues the five-minute token. Not one transaction: a failure must stay
     * counted although an exception follows it.
     *
     * @throws IllegalArgumentException when either is wrong, or the account is
     *         locked: a 400, not a 401, which the frontend would take for an
     *         ended session
     */
    public String confirm(String password, String code) {
        Users user = currentUserResolver.resolve();
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        if (LoginLockout.isLocked(user, now)) {
            throw new IllegalArgumentException(LoginLockout.LOCKED_MESSAGE);
        }

        boolean ok = password != null && passwordEncoder.matches(password, user.getPasswordHash());
        if (ok && user.isTotpEnabled()) {
            ok = twoFactorAuthService.verifyCurrentCode(user, code);
        }
        if (!ok) {
            boolean lockedNow = LoginLockout.recordFailure(user, now);
            usersRepository.save(user);
            loginAttemptMonitor.passwordFailed(user);
            if (lockedNow) {
                loginAttemptMonitor.locked(user);
            }
            throw new IllegalArgumentException(WRONG);
        }
        if (LoginLockout.recordSuccess(user)) {
            usersRepository.save(user);
        }
        return jwtUtils.generateReauthToken(user);
    }

    /**
     * Throws unless the current request carries a re-authentication token of
     * the signed-in user, in their organization, less than five minutes old.
     *
     * @throws ReauthenticationRequiredException answered 428
     */
    public void require() {
        String token = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            token = attributes.getRequest().getHeader(HEADER);
        }
        if (!jwtUtils.isReauthTokenFor(token, AuthenticatedUserUtils.getUsernameOrNull(), TenantContext.get())) {
            throw new ReauthenticationRequiredException(REQUIRED);
        }
    }
}
