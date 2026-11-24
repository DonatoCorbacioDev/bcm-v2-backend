/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.security.PublicDemoGuard;
import com.donatodev.bcm_backend.service.AuditLogService;
import com.donatodev.bcm_backend.service.IEmailService;

/** When a sign-in from another kind of device is told to the account's owner, and when not. */
class NewDeviceMonitorTest {

    private static final String WINDOWS_CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";
    private static final String IPHONE_SAFARI =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Version/17.5 Safari/604.1";

    private final UsersRepository users = mock(UsersRepository.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final IEmailService email = mock(IEmailService.class);
    private final PublicDemoGuard demo = mock(PublicDemoGuard.class);
    private final Executor sameThread = Runnable::run;
    private final NewDeviceMonitor monitor = new NewDeviceMonitor(users, audit, email, demo, sameThread);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void requestFrom(String userAgent, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", userAgent);
        request.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static Users user(String lastDevice) {
        return Users.builder().username("anna").lastLoginDevice(lastDevice)
                .manager(Managers.builder().email("anna@studio.example").build()).build();
    }

    @Test
    @DisplayName("another kind of device: its owner gets an email saying which, from where, and what to do")
    void newDeviceIsTold() {
        Users anna = user("Chrome su Windows");
        requestFrom(IPHONE_SAFARI, "203.0.113.5");

        monitor.signedIn(anna);

        assertThat(anna.getLastLoginDevice()).isEqualTo("Safari su iPhone");
        verify(users).save(anna);
        verify(audit).saveAboutUser(eq("NEW_DEVICE_SIGN_IN"), eq(anna), contains("Safari su iPhone"));
        verify(email).sendEmail(eq("anna@studio.example"), eq(NewDeviceMonitor.SUBJECT), contains("203.0.113.5"));
    }

    @Test
    @DisplayName("the same kind of device as last time, even from another address: no email")
    void sameDeviceIsQuiet() {
        Users anna = user("Chrome su Windows");
        requestFrom(WINDOWS_CHROME, "198.51.100.77");

        monitor.signedIn(anna);

        verify(email, never()).sendEmail(anyString(), anyString(), anyString());
        verify(users, never()).save(anna);
    }

    @Test
    @DisplayName("the first sign-in known: the device is remembered, nothing is sent")
    void firstSignInIsQuiet() {
        Users anna = user(null);
        requestFrom(WINDOWS_CHROME, "198.51.100.77");

        monitor.signedIn(anna);

        assertThat(anna.getLastLoginDevice()).isEqualTo("Chrome su Windows");
        verify(email, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("the public demo account, used by everyone, is never told anything")
    void publicDemoIsQuiet() {
        Users visitor = user("Chrome su Windows");
        when(demo.isPublicDemo("anna")).thenReturn(true);
        requestFrom(IPHONE_SAFARI, "203.0.113.5");

        monitor.signedIn(visitor);

        verify(email, never()).sendEmail(anyString(), anyString(), anyString());
        assertThat(visitor.getLastLoginDevice()).isEqualTo("Chrome su Windows");
    }

    @Test
    @DisplayName("what the email shows is encoded: a User-Agent is chosen by whoever signs in")
    void bodyIsEncoded() {
        String body = NewDeviceMonitor.emailBody("anna", "<b>x</b> su y", "203.0.113.5",
                ZonedDateTime.of(2026, 11, 24, 14, 32, 0, 0, ZoneId.of("Europe/Rome")));
        assertThat(body).contains("&lt;b&gt;x&lt;/b&gt; su y").doesNotContain("<b>x</b>")
                .contains("24/11/2026 alle 14:32");
    }
}
