package com.donatodev.bcm_backend.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.auth.AuthResponseDTO;
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

    private final UsersRepository usersRepository;
    private final TotpRecoveryCodeRepository recoveryCodeRepository;
    private final TotpEncryptionService totpEncryptionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final CurrentUserResolver currentUserResolver;

    public TwoFactorAuthService(UsersRepository usersRepository,
                                TotpRecoveryCodeRepository recoveryCodeRepository,
                                TotpEncryptionService totpEncryptionService,
                                PasswordEncoder passwordEncoder,
                                JwtUtils jwtUtils,
                                RefreshTokenService refreshTokenService,
                                CurrentUserResolver currentUserResolver) {
        this.usersRepository = usersRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.totpEncryptionService = totpEncryptionService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.currentUserResolver = currentUserResolver;
    }

    @Transactional
    public TotpSetupResponse setup() {
        Users user = currentUserResolver.resolve();
        String secret = TotpUtil.generateSecret();
        user.setTotpSecretEncrypted(totpEncryptionService.encrypt(secret));
        user.setTotpEnabled(false);
        usersRepository.save(user);

        return new TotpSetupResponse(secret, TotpUtil.buildOtpAuthUri(secret, user.getUsername(), ISSUER));
    }

    @Transactional
    public TotpConfirmResponse confirm(String code) {
        Users user = currentUserResolver.resolve();
        if (user.getTotpSecretEncrypted() == null) {
            throw new IllegalArgumentException("Eseguire prima il setup per confermare la 2FA");
        }

        String secret = totpEncryptionService.decrypt(user.getTotpSecretEncrypted());
        if (!TotpUtil.verifyCode(secret, code)) {
            throw new IllegalArgumentException("Codice di verifica non valido");
        }

        user.setTotpEnabled(true);
        usersRepository.save(user);

        recoveryCodeRepository.deleteByUserId(user.getId());
        List<String> plainCodes = generateRecoveryCodes();
        List<TotpRecoveryCode> entities = plainCodes.stream()
                .map(plain -> TotpRecoveryCode.builder().user(user).codeHash(passwordEncoder.encode(plain)).build())
                .toList();
        recoveryCodeRepository.saveAll(entities);

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
            throw new IllegalArgumentException("Codice di verifica non valido");
        }

        user.setTotpEnabled(false);
        user.setTotpSecretEncrypted(null);
        usersRepository.save(user);
        recoveryCodeRepository.deleteByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public TotpStatusResponse status() {
        return new TotpStatusResponse(currentUserResolver.resolve().isTotpEnabled());
    }

    /**
     * Second step of a two-step login: exchanges a short-lived MFA-pending
     * token plus a TOTP/recovery code for the real access and refresh tokens.
     */
    @Transactional
    public AuthResponseDTO verifyLogin(String mfaToken, String code) {
        if (!jwtUtils.isMfaPendingToken(mfaToken)) {
            throw new BadCredentialsException(INVALID_MFA_SESSION);
        }

        String username = jwtUtils.getUsernameFromToken(mfaToken);
        Long orgId = jwtUtils.getOrganizationIdFromToken(mfaToken);

        Users user = (orgId != null
                ? usersRepository.findByUsernameAndOrganizationId(username, orgId)
                : usersRepository.findByUsername(username))
                .orElseThrow(() -> new BadCredentialsException(INVALID_MFA_SESSION));

        if (!user.isTotpEnabled() || user.getTotpSecretEncrypted() == null) {
            throw new BadCredentialsException(INVALID_MFA_SESSION);
        }

        String secret = totpEncryptionService.decrypt(user.getTotpSecretEncrypted());
        boolean valid = TotpUtil.verifyCode(secret, code) || consumeRecoveryCodeIfValid(user, code);
        if (!valid) {
            throw new BadCredentialsException("Codice di verifica non valido");
        }

        String accessToken = jwtUtils.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponseDTO(accessToken, refreshToken);
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
