package com.donatodev.bcm_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.security.SecurityEvents;
import com.donatodev.bcm_backend.service.IEmailService;

/** OWASP JWT Cheat Sheet: suspicious sign-ins are recorded, and a locked account's owner is told. */
class LoginAttemptMonitorTest {

    private static final UUID ORG = UUID.randomUUID();

    private final SecurityEvents events = mock(SecurityEvents.class);
    private final IEmailService email = mock(IEmailService.class);
    /** Runs the mail at once, on the test's thread. */
    private final LoginAttemptMonitor monitor = new LoginAttemptMonitor(events, email, Runnable::run);

    private static Users user(String username, String address) {
        return Users.builder().username(username).organization(Organization.builder().id(ORG).build())
                .manager(address == null ? null : Managers.builder().email(address).build()).build();
    }

    @Test
    @DisplayName("a wrong password and a wrong code are recorded against the account and its organization")
    void failuresAreRecorded() {
        Users anna = user("anna", "anna@example.com");

        monitor.passwordFailed(anna);
        monitor.codeFailed(anna);

        verify(events).recordFor(SecurityEvents.Type.LOGIN_FAILED, "anna", ORG, null);
        verify(events).recordFor(SecurityEvents.Type.MFA_FAILED, "anna", ORG, null);
        verify(email, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a lock is recorded and its owner gets an email, the name escaped")
    void lockIsToldByEmail() {
        monitor.locked(user("<b>anna</b>", "anna@example.com"));

        verify(events).recordFor(eq(SecurityEvents.Type.ACCOUNT_LOCKED), eq("<b>anna</b>"), eq(ORG), anyString());
        verify(email).sendEmail(eq("anna@example.com"), eq(LoginAttemptMonitor.LOCKED_SUBJECT),
                org.mockito.ArgumentMatchers.argThat(body -> body.contains("&lt;b&gt;anna&lt;/b&gt;")
                        && body.contains(LoginLockout.LOCKOUT_MINUTES + " minuti")));
    }

    @Test
    @DisplayName("an account with no address is still recorded, and no email is attempted")
    void noAddressNoEmail() {
        monitor.locked(user("anna", null));
        monitor.locked(user("anna", " "));

        verify(email, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a failed or dropped email never fails the login that caused it")
    void mailFailuresAreContained() {
        doThrow(new IllegalStateException("smtp down")).when(email).sendEmail(anyString(), anyString(), anyString());
        monitor.locked(user("anna", "anna@example.com"));

        Executor full = command -> {
            throw new RejectedExecutionException("full");
        };
        new LoginAttemptMonitor(events, email, full).locked(user("bob", "bob@example.com"));

        assertThat(LoginAttemptMonitor.lockedEmailBody("x")).contains("password");
    }
}
