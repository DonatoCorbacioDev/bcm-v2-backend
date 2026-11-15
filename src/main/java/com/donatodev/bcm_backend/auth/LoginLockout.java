package com.donatodev.bcm_backend.auth;

import java.time.LocalDateTime;

import org.springframework.security.authentication.LockedException;

import com.donatodev.bcm_backend.entity.Users;

/**
 * The account lock shared by both login steps: a wrong password and a wrong
 * 2FA code count against the same {@link #MAX_FAILED_ATTEMPTS}, so knowing
 * the password still leaves only a handful of guesses at the 6-digit code
 * every {@link #LOCKOUT_MINUTES} minutes (OWASP API2).
 *
 * <p>Works on the entity only; the caller saves it.
 */
public final class LoginLockout {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCKOUT_MINUTES = 15;

    static final String LOCKED_MESSAGE =
            "Account temporaneamente bloccato per troppi tentativi falliti. Riprova più tardi.";

    private LoginLockout() {
    }

    /**
     * Refuses a locked account, saying so: used at the 2FA step, where the
     * caller has already proved the password. A lock that has run out is
     * cleared, and the attempt count starts again from zero.
     *
     * @throws LockedException while the lock is still running
     */
    public static void ensureNotLocked(Users user, LocalDateTime now) {
        if (isLocked(user, now)) {
            throw new LockedException(LOCKED_MESSAGE);
        }
    }

    /**
     * Whether the lock is still running, without saying so to the caller: the
     * password step answers a locked account like a wrong password, or the
     * answer would tell that the account exists. A lock that has run out is
     * cleared, and the attempt count starts again from zero.
     */
    public static boolean isLocked(Users user, LocalDateTime now) {
        if (user.getLockedUntil() == null) {
            return false;
        }
        if (user.getLockedUntil().isAfter(now)) {
            return true;
        }
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        return false;
    }

    /**
     * Counts one failed attempt, locking the account once the limit is reached.
     *
     * @return whether this attempt locked the account
     */
    public static boolean recordFailure(Users user, LocalDateTime now) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(now.plusMinutes(LOCKOUT_MINUTES));
            return true;
        }
        return false;
    }

    /**
     * Starts the count again after a successful step.
     *
     * @return whether the entity changed and needs saving
     */
    public static boolean recordSuccess(Users user) {
        if (user.getFailedLoginAttempts() == 0) {
            return false;
        }
        user.setFailedLoginAttempts(0);
        return true;
    }
}
