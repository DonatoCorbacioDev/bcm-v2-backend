/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.security.SecurityEvents;
import com.donatodev.bcm_backend.service.IEmailService;

import jakarta.annotation.PreDestroy;

/**
 * Watches the sign-ins (OWASP Authentication and Session Management Cheat Sheet: monitor suspicious login
 * attempts): a wrong password or 2FA code on an existing account, and the
 * lock that follows too many of them, become security events in the audit
 * and the weekly report. The owner of a locked account is told by email,
 * since the login page answers a locked account like a wrong password.
 */
@Component
public class LoginAttemptMonitor {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptMonitor.class);
    private static final int QUEUE_CAPACITY = 100;
    public static final String LOCKED_SUBJECT = "BCM: il tuo account è stato bloccato temporaneamente";

    private final SecurityEvents securityEvents;
    private final IEmailService emailService;
    private final Executor executor;
    private final ExecutorService ownedExecutor;

    @Autowired
    public LoginAttemptMonitor(SecurityEvents securityEvents, IEmailService emailService) {
        this(securityEvents, emailService, newExecutor());
    }

    LoginAttemptMonitor(SecurityEvents securityEvents, IEmailService emailService, Executor executor) {
        this.securityEvents = securityEvents;
        this.emailService = emailService;
        this.executor = executor;
        this.ownedExecutor = executor instanceof ExecutorService service ? service : null;
    }

    private static ExecutorService newExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "account-locked-mail");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** A wrong password for the account. */
    public void passwordFailed(Users user) {
        securityEvents.recordFor(SecurityEvents.Type.LOGIN_FAILED, user.getUsername(), orgId(user), null);
    }

    /** A wrong 2FA or recovery code for the account. */
    public void codeFailed(Users user) {
        securityEvents.recordFor(SecurityEvents.Type.MFA_FAILED, user.getUsername(), orgId(user), null);
    }

    /** The attempt that just locked the account: recorded, and its owner told off the request thread. */
    public void locked(Users user) {
        securityEvents.recordFor(SecurityEvents.Type.ACCOUNT_LOCKED, user.getUsername(), orgId(user),
                "per " + LoginLockout.LOCKOUT_MINUTES + " minuti");
        String email = user.getManager() == null ? null : user.getManager().getEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        String body = lockedEmailBody(user.getUsername());
        try {
            executor.execute(() -> send(email, body));
        } catch (RejectedExecutionException e) {
            logger.warn("Account locked email dropped: mail queue full");
        }
    }

    static String lockedEmailBody(String username) {
        return "<p>Il tuo account BCM <b>" + HtmlUtils.htmlEscape(username) + "</b> è stato bloccato per "
                + LoginLockout.LOCKOUT_MINUTES + " minuti dopo " + LoginLockout.MAX_FAILED_ATTEMPTS
                + " tentativi di accesso falliti.</p>"
                + "<p>Durante il blocco anche la password giusta viene rifiutata: riprova tra "
                + LoginLockout.LOCKOUT_MINUTES + " minuti.</p>"
                + "<p>Se non sei stato tu, qualcuno sta provando a indovinare la tua password: "
                + "cambiala appena rientri e attiva l'autenticazione a due fattori dal tuo profilo.</p>";
    }

    private void send(String email, String body) {
        try {
            emailService.sendEmail(email, LOCKED_SUBJECT, body);
        } catch (RuntimeException e) {
            // The exception's message may carry the address: only its type is logged.
            logger.error("Account locked email not sent: {}", e.getClass().getSimpleName());
        }
    }

    private static UUID orgId(Users user) {
        return user.getOrganization() == null ? null : user.getOrganization().getId();
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
            if (!ownedExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                ownedExecutor.shutdownNow();
            }
        }
    }
}
