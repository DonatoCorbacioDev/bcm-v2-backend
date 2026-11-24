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

    /**
     * A new password or a 2FA change: every session ends, a pending reset link
     * is deleted, and so is the calendar feed's link, which a session may have
     * read and kept (it opens the feed without signing in). A new one is made
     * the next time the profile shows it.
     */
    @Transactional
    public void credentialsChanged(Users user) {
        user.setCalendarToken(null);
        endSessions(user);
        passwordResetTokenRepository.findByUser(user).ifPresent(passwordResetTokenRepository::delete);
    }

    /**
     * A stolen session shown by a refresh token used twice: every session ends
     * and the calendar feed's link is deleted, as on a credential change. The
     * reset link stays: its owner may be using it right now to take the account back.
     */
    @Transactional
    public void sessionsCompromised(Users user) {
        user.setCalendarToken(null);
        endSessions(user);
    }

    /**
     * Ends every session of the user at once (OWASP Authentication and Session Management Cheat Sheet: logout and
     * revocation): the live refresh tokens are deleted, and the access tokens issued
     * before now are refused by {@code JwtUtils} although not yet expired. Used
     * on logout, since an account has one session at a time, and when a stolen
     * refresh token is replayed.
     */
    @Transactional
    public void endSessions(Users user) {
        user.setCredentialsChangedAt(clock.instant().getEpochSecond());
        usersRepository.save(user);
        refreshTokenRepository.deleteActiveByUser(user);
    }
}
