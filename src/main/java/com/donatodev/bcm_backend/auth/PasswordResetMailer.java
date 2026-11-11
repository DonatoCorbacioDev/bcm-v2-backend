/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.service.IEmailService;
import com.donatodev.bcm_backend.service.PasswordResetTokenService;
import com.donatodev.bcm_backend.service.UserService;

import jakarta.annotation.PreDestroy;

/**
 * Sends the "forgot password" email off the request thread (OWASP API2).
 * Looking the address up, creating the link and talking to the mail server
 * all happen here, so {@code POST /auth/forgot-password} answers at once and
 * the same way whether the address has an account or not: neither the
 * response time (a mail server takes hundreds of milliseconds) nor a mail
 * failure (which would be a 500 only for a real account) tells them apart.
 *
 * <p>One thread, so two requests for the same account never race on its
 * link; a bounded queue, so a burst cannot pile up work. A request that finds
 * the queue full is dropped: the person can simply ask again.
 */
@Component
public class PasswordResetMailer {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetMailer.class);
    private static final int QUEUE_CAPACITY = 100;

    private final UserService userService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final IEmailService emailService;
    private final String frontendBaseUrl;
    private final Executor executor;
    private final ExecutorService ownedExecutor;

    @Autowired
    public PasswordResetMailer(UserService userService, PasswordResetTokenService passwordResetTokenService,
            IEmailService emailService, @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this(userService, passwordResetTokenService, emailService, frontendBaseUrl, newExecutor());
    }

    PasswordResetMailer(UserService userService, PasswordResetTokenService passwordResetTokenService,
            IEmailService emailService, String frontendBaseUrl, Executor executor) {
        this.userService = userService;
        this.passwordResetTokenService = passwordResetTokenService;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
        this.executor = executor;
        this.ownedExecutor = executor instanceof ExecutorService service ? service : null;
    }

    private static ExecutorService newExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "password-reset-mail");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Queues the email and returns at once; never throws. */
    public void requestReset(String email) {
        try {
            executor.execute(() -> send(email));
        } catch (RejectedExecutionException e) {
            logger.warn("Password reset request dropped: mail queue full");
        }
    }

    private void send(String email) {
        try {
            userService.findByEmail(email).ifPresent(user -> {
                String token = passwordResetTokenService.createToken(user);
                emailService.sendResetPasswordEmail(email, frontendBaseUrl + "/reset-password?token=" + token);
            });
        } catch (RuntimeException e) {
            // The exception's message may carry the address: only its type is logged.
            logger.error("Password reset email not sent: {}", e.getClass().getSimpleName());
        }
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
