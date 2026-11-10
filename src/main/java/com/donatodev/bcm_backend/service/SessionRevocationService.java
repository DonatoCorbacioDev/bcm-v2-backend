package com.donatodev.bcm_backend.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.PasswordResetTokenRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Ends every session of a user whose credentials just changed (OWASP API2):
 * a new password or 2FA turned on or off must lock out whoever held a session
 * opened with the old ones.
 *
 * <ul>
 *   <li>every refresh token is revoked, so no session can be renewed;</li>
 *   <li>{@code credentialsChangedAt} is set, so access tokens issued before
 *       now are refused at once instead of in up to 15 minutes;</li>
 *   <li>a pending password reset link is deleted: it was asked for the old
 *       password.</li>
 * </ul>
 *
 * <p>A change the user makes on their own session (own password, own 2FA)
 * hands that session a new refresh cookie afterwards, so only the others end.
 */
@Service
public class SessionRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UsersRepository usersRepository;
    private Clock clock = Clock.systemUTC();

    public SessionRevocationService(RefreshTokenRepository refreshTokenRepository,
                                    PasswordResetTokenRepository passwordResetTokenRepository,
                                    UsersRepository usersRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.usersRepository = usersRepository;
    }

    /** For tests: a fixed clock. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional
    public void credentialsChanged(Users user) {
        user.setCredentialsChangedAt(clock.instant().getEpochSecond());
        usersRepository.save(user);
        refreshTokenRepository.revokeAllByUser(user);
        passwordResetTokenRepository.findByUser(user).ifPresent(passwordResetTokenRepository::delete);
    }
}
