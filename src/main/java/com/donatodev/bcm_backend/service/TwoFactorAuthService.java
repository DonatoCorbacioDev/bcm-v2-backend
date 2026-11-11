/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.UUID;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.auth.AuthResponseDTO;
import com.donatodev.bcm_backend.auth.LoginLockout;
import com.donatodev.bcm_backend.dto.TotpConfirmResponse;
import com.donatodev.bcm_backend.dto.TotpSetupResponse;
import com.donatodev.bcm_backend.dto.TotpStatusResponse;
import com.donatodev.bcm_backend.entity.TotpRecoveryCode;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.TotpRecoveryCodeRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.TotpUtil;

/**
 * Orchestrates TOTP-based two-factor authentication: enrollment (setup +
 * confirm), disabling, status, and the second step of a two-step login.
 */
@Service
public class TwoFactorAuthService {

    private static final String ISSUER = "BCM";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final String RECOVERY_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no O/0/I/1
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String INVALID_MFA_SESSION = "Sessione MFA non valida o scaduta";
    private static final String INVALID_CODE = "Codice di verifica non valido";

    private final UsersRepository usersRepository;
    private final TotpRecoveryCodeRepository recoveryCodeRepository;
    private final TotpEncryptionService totpEncryptionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final CurrentUserResolver currentUserResolver;
    private final SessionRevocationService sessionRevocationService;

    public TwoFactorAuthService(UsersRepository usersRepository,
                                TotpRecoveryCodeRepository recoveryCodeRepository,
                                TotpEncryptionService totpEncryptionService,
                                PasswordEncoder passwordEncoder,
                                JwtUtils jwtUtils,
                                RefreshTokenService refreshTokenService,
                                CurrentUserResolver currentUserResolver,
                                SessionRevocationService sessionRevocationService) {
        this.usersRepository = usersRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.totpEncryptionService = totpEncryptionService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.currentUserResolver = currentUserResolver;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Transactional
    public TotpSetupResponse setup() {
        return setupFor(currentUserResolver.resolve());
    }

    /**
     * A new secret, not yet active. Refused while 2FA is on: overwriting the
     * secret used to turn 2FA off without a code, so a stolen session could
     * drop it. Changing the device goes through {@link #disable} first.
     */
    private TotpSetupResponse setupFor(Users user) {
        if (user.isTotpEnabled()) {
            throw new IllegalArgumentException("La 2FA è già attiva: per cambiare dispositivo disattivala prima con un codice");
        }
        String secret = TotpUtil.generateSecret();
        user.setTotpSecretEncrypted(totpEncryptionService.encrypt(secret));
        user.setTotpEnabled(false);
        usersRepository.save(user);

        return new TotpSetupResponse(secret, TotpUtil.buildOtpAuthUri(secret, user.getUsername(), ISSUER));
    }

    @Transactional
    public TotpConfirmResponse confirm(String code) {
        return confirmFor(currentUserResolver.resolve(), code);
    }

    private TotpConfirmResponse confirmFor(Users user, String code) {
        if (user.isTotpEnabled()) {
            throw new IllegalArgumentException("La 2FA è già attiva");
        }
        if (user.getTotpSecretEncrypted() == null) {
            throw new IllegalArgumentException("Eseguire prima il setup per confermare la 2FA");
        }

        String secret = totpEncryptionService.decrypt(user.getTotpSecretEncrypted());
        if (!TotpUtil.verifyCode(secret, code)) {
            throw new IllegalArgumentException(INVALID_CODE);
        }

        user.setTotpEnabled(true);
        usersRepository.save(user);

        recoveryCodeRepository.deleteByUserId(user.getId());
        List<String> plainCodes = generateRecoveryCodes();
        List<TotpRecoveryCode> entities = plainCodes.stream()
                .map(plain -> TotpRecoveryCode.builder().user(user).codeHash(passwordEncoder.encode(plain)).build())
                .toList();
        recoveryCodeRepository.saveAll(entities);
        // Sessions opened without 2FA end: from now on a session needs the code.
        sessionRevocationService.credentialsChanged(user);

        return new TotpConfirmResponse(plainCodes);
    }

    @Transactional
    public void disable(String code) {
        Users user = currentUserResolver.resolve();
        if (!user.isTotpEnabled() || user.getTotpSecretEncrypted() == null) {
            throw new IllegalArgumentException("La 2FA non è attiva");
        }

        String secret = totpEncryptionService.decrypt(user.getTotpSecretEncrypted());
        if (!TotpUtil.verifyCode(secret, code) && !consumeRecoveryCodeIfValid(user, code)) {
            throw new IllegalArgumentException(INVALID_CODE);
        }

        user.setTotpEnabled(false);
        user.setTotpSecretEncrypted(null);
        usersRepository.save(user);
        recoveryCodeRepository.deleteByUserId(user.getId());
        sessionRevocationService.credentialsChanged(user);
    }

    /**
     * First step of the 2FA setup an account must do before its first session
     * ({@code TwoFactorPolicy}), authorized by the setup token from login
     * instead of a session.
     */
    @Transactional
    public TotpSetupResponse enrollSetup(String setupToken) {
        return setupFor(userFromSetupToken(setupToken));
    }

    /**
     * Second step: the code from the new authenticator turns 2FA on, and only
     * then the account gets its session, with the recovery codes to save.
     */
    @Transactional
    public EnrollmentResult enrollConfirm(String setupToken, String code) {
        Users user = userFromSetupToken(setupToken);
        TotpConfirmResponse codes = confirmFor(user, code);
        AuthResponseDTO session = new AuthResponseDTO(jwtUtils.generateToken(user),
                refreshTokenService.createRefreshToken(user));
        return new EnrollmentResult(codes.recoveryCodes(), session);
    }

    public record EnrollmentResult(List<String> recoveryCodes, AuthResponseDTO session) {}

    private Users userFromSetupToken(String setupToken) {
        if (!jwtUtils.isMfaSetupToken(setupToken)) {
            throw new BadCredentialsException(INVALID_MFA_SESSION);
        }
        UUID orgId = jwtUtils.getOrganizationIdFromToken(setupToken);
        if (orgId == null) {
            throw new BadCredentialsException(INVALID_MFA_SESSION);
        }
        return usersRepository.findByUsernameAndOrganizationId(jwtUtils.getUsernameFromToken(setupToken), orgId)
                .orElseThrow(() -> new BadCredentialsException(INVALID_MFA_SESSION));
    }

    @Transactional(readOnly = true)
    public TotpStatusResponse status() {
        return new TotpStatusResponse(currentUserResolver.resolve().isTotpEnabled());
    }

    /**
     * Second step of a two-step login: exchanges a short-lived MFA-pending
     * token plus a TOTP/recovery code for the real access and refresh tokens.
     *
     * <p>A wrong code counts against the same lock as a wrong password
     * ({@link LoginLockout}), and a TOTP code is accepted once: only for a
     * time step after the last one used. The count must survive the failure,
     * hence no rollback for the two exceptions thrown on a bad attempt.
     */
    @Transactional(noRollbackFor = { BadCredentialsException.class, LockedException.class })
    public AuthResponseDTO verifyLogin(String mfaToken, String code) {
        // A setup token carries mfaPending too, but proves only the password.
        if (!jwtUtils.isMfaPendingToken(mfaToken) || jwtUtils.isMfaSetupToken(mfaToken)) {
            throw new BadCredentialsException(INVALID_MFA_SESSION);
        }

        String username = jwtUtils.getUsernameFromToken(mfaToken);
        UUID orgId = jwtUtils.getOrganizationIdFromToken(mfaToken);

        // Fails closed: a token without an organization identifies nobody.
        if (orgId == null) {
            throw new BadCredentialsException(INVALID_MFA_SESSION);
        }
        Users user = usersRepository.findByUsernameAndOrganizationId(username, orgId)
                .orElseThrow(() -> new BadCredentialsException(INVALID_MFA_SESSION));

        if (!user.isTotpEnabled() || user.getTotpSecretEncrypted() == null) {
            throw new BadCredentialsException(INVALID_MFA_SESSION);
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LoginLockout.ensureNotLocked(user, now);

        String secret = totpEncryptionService.decrypt(user.getTotpSecretEncrypted());
        if (!acceptTotpCode(user, secret, code) && !consumeRecoveryCodeIfValid(user, code)) {
            LoginLockout.recordFailure(user, now);
            usersRepository.save(user);
            throw new BadCredentialsException(INVALID_CODE);
        }
        LoginLockout.recordSuccess(user);
        usersRepository.save(user);

        String accessToken = jwtUtils.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponseDTO(accessToken, refreshToken);
    }

    /**
     * Accepts a TOTP code only for a time step later than the last one used
     * at login, and remembers the step: the same code can't log in twice.
     */
    private static boolean acceptTotpCode(Users user, String secret, String code) {
        long step = TotpUtil.matchingStep(secret, code);
        Long lastUsed = user.getTotpLastUsedStep();
        if (step < 0 || (lastUsed != null && step <= lastUsed)) {
            return false;
        }
        user.setTotpLastUsedStep(step);
        return true;
    }

    private boolean consumeRecoveryCodeIfValid(Users user, String code) {
        List<TotpRecoveryCode> unused = recoveryCodeRepository.findByUserIdAndUsedAtIsNull(user.getId());
        for (TotpRecoveryCode candidate : unused) {
            if (passwordEncoder.matches(code, candidate.getCodeHash())) {
                candidate.setUsedAt(Instant.now());
                recoveryCodeRepository.save(candidate);
                return true;
            }
        }
        return false;
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(randomRecoveryCode());
        }
        return codes;
    }

    private String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(RECOVERY_CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(RECOVERY_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

}
