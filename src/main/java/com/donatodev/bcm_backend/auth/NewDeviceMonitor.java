/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.HtmlUtils;

import com.donatodev.bcm_backend.dto.RequestOrigin;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.security.PublicDemoGuard;
import com.donatodev.bcm_backend.service.AuditLogService;
import com.donatodev.bcm_backend.service.IEmailService;
import com.donatodev.bcm_backend.util.DeviceSummary;

import jakarta.annotation.PreDestroy;

/**
 * Emails an account's owner when it signs in from another kind of device than
 * last time (OWASP ASVS 6.3, unusual sign-ins; how Google, banks and GitHub do
 * it). An account has one session at a time, so a thief signing in only
 * signs its owner out, which looks like a glitch; this email says what
 * happened, from where, and what to do. Called at every new session
 * (RefreshTokenService.createRefreshToken), never at a renewal.
 * <p>
 * "Kind of device" is the browser's family and the system (DeviceSummary): the
 * full User-Agent changes with every update, the address with every network,
 * and either would send an email for nothing. The address only goes into the
 * email, as information. The first sign-in after this was introduced, and the
 * public demo account, send none. The email leaves off the request thread, as
 * LoginAttemptMonitor's: signing in never waits on the mail server.
 */
@Component
public class NewDeviceMonitor {

    private static final Logger logger = LoggerFactory.getLogger(NewDeviceMonitor.class);
    private static final int QUEUE_CAPACITY = 100;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'alle' HH:mm");
    private static final ZoneId ITALY = ZoneId.of("Europe/Rome");
    public static final String SUBJECT = "BCM: nuovo accesso al tuo account";

    private final UsersRepository usersRepository;
    private final AuditLogService auditLogService;
    private final IEmailService emailService;
    private final PublicDemoGuard publicDemoGuard;
    private final Executor executor;
    private final ExecutorService ownedExecutor;

    @Autowired
    public NewDeviceMonitor(UsersRepository usersRepository, AuditLogService auditLogService,
            IEmailService emailService, PublicDemoGuard publicDemoGuard) {
        this(usersRepository, auditLogService, emailService, publicDemoGuard, newExecutor());
    }

    NewDeviceMonitor(UsersRepository usersRepository, AuditLogService auditLogService, IEmailService emailService,
            PublicDemoGuard publicDemoGuard, Executor executor) {
        this.usersRepository = usersRepository;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
        this.publicDemoGuard = publicDemoGuard;
        this.executor = executor;
        this.ownedExecutor = executor instanceof ExecutorService service ? service : null;
    }

    private static ExecutorService newExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "new-device-mail");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** A new session of {@code user}, from the current request's device and address. */
    public void signedIn(Users user) {
        if (publicDemoGuard.isPublicDemo(user.getUsername())) {
            return;
        }
        RequestOrigin origin = currentOrigin();
        String device = DeviceSummary.of(origin == null ? null : origin.userAgent());
        String previous = user.getLastLoginDevice();
        if (device.equals(previous)) {
            return;
        }
        user.setLastLoginDevice(device);
        usersRepository.save(user);
        if (previous == null) {
            return;
        }
        String ip = origin == null || origin.ip() == null ? "sconosciuto" : origin.ip();
        auditLogService.saveAboutUser("NEW_DEVICE_SIGN_IN", user, "device=" + device + " ip=" + ip);
        String email = user.getManager() == null ? null : user.getManager().getEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        String body = emailBody(user.getUsername(), device, ip, ZonedDateTime.now(ITALY));
        try {
            executor.execute(() -> send(email, body));
        } catch (RejectedExecutionException e) {
            logger.warn("New device email dropped: mail queue full");
        }
    }

    static String emailBody(String username, String device, String ip, ZonedDateTime when) {
        return "<p>Nuovo accesso al tuo account BCM <b>" + HtmlUtils.htmlEscape(username) + "</b>:</p>"
                + "<ul><li>Dispositivo: <b>" + HtmlUtils.htmlEscape(device) + "</b></li>"
                + "<li>Indirizzo: " + HtmlUtils.htmlEscape(ip) + "</li>"
                + "<li>Quando: " + HtmlUtils.htmlEscape(WHEN.format(when)) + "</li></ul>"
                + "<p>Se sei stato tu, non devi fare nulla.</p>"
                + "<p>Se non sei stato tu, qualcuno conosce la tua password: accedi, cambiala subito dal "
                + "tuo profilo e attiva l'autenticazione a due fattori. Cambiando la password, ogni "
                + "altra sessione viene chiusa.</p>";
    }

    private void send(String email, String body) {
        try {
            emailService.sendEmail(email, SUBJECT, body);
        } catch (RuntimeException e) {
            // The exception's message may carry the address: only its type is logged.
            logger.error("New device email not sent: {}", e.getClass().getSimpleName());
        }
    }

    private static RequestOrigin currentOrigin() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return RequestOrigin.from(attributes.getRequest());
        }
        return null;
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
