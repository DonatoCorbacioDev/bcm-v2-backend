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
     * Refuses a locked account. A lock that has run out is cleared, and the
     * attempt count starts again from zero.
     *
     * @throws LockedException while the lock is still running
     */
    public static void ensureNotLocked(Users user, LocalDateTime now) {
        if (user.getLockedUntil() == null) {
            return;
        }
        if (user.getLockedUntil().isAfter(now)) {
            throw new LockedException(LOCKED_MESSAGE);
        }
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
    }

    /** Counts one failed attempt, locking the account once the limit is reached. */
    public static void recordFailure(Users user, LocalDateTime now) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(now.plusMinutes(LOCKOUT_MINUTES));
        }
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
