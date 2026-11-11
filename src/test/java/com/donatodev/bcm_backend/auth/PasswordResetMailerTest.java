package com.donatodev.bcm_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.service.IEmailService;
import com.donatodev.bcm_backend.service.PasswordResetTokenService;
import com.donatodev.bcm_backend.service.UserService;

class PasswordResetMailerTest {

    private static final String FRONTEND = "https://bcm.test";

    private final UserService userService = mock(UserService.class);
    private final PasswordResetTokenService tokenService = mock(PasswordResetTokenService.class);
    private final IEmailService emailService = mock(IEmailService.class);

    /** Runs the task on the calling thread, so the outcome can be checked at once. */
    private PasswordResetMailer inline() {
        return new PasswordResetMailer(userService, tokenService, emailService, FRONTEND, Runnable::run);
    }

    @Test
    @DisplayName("A known address gets a link carrying the plain token")
    void knownAddressGetsALink() {
        Users user = new Users();
        when(userService.findByEmail("anna@alfa.test")).thenReturn(Optional.of(user));
        when(tokenService.createToken(user)).thenReturn("plain-token");

        inline().requestReset("anna@alfa.test");

        verify(emailService).sendResetPasswordEmail("anna@alfa.test", FRONTEND + "/reset-password?token=plain-token");
    }

    @Test
    @DisplayName("An unknown address creates no link and sends nothing")
    void unknownAddressDoesNothing() {
        when(userService.findByEmail("nobody@alfa.test")).thenReturn(Optional.empty());

        inline().requestReset("nobody@alfa.test");

        verify(tokenService, never()).createToken(any());
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("A mail server failure stays inside the mailer: the request still succeeds")
    void mailFailureIsSwallowed() {
        Users user = new Users();
        when(userService.findByEmail("anna@alfa.test")).thenReturn(Optional.of(user));
        when(tokenService.createToken(user)).thenReturn("plain-token");
        doThrow(new MailSendException("smtp down for anna@alfa.test"))
                .when(emailService).sendResetPasswordEmail(anyString(), anyString());

        assertThatCode(() -> inline().requestReset("anna@alfa.test")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A full queue drops the request without failing it")
    void fullQueueIsDropped() {
        PasswordResetMailer mailer = new PasswordResetMailer(userService, tokenService, emailService, FRONTEND,
                task -> {
                    throw new RejectedExecutionException("full");
                });

        assertThatCode(() -> mailer.requestReset("anna@alfa.test")).doesNotThrowAnyException();
        verify(userService, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("The real executor answers before the mail server does, on its own thread")
    void returnsBeforeTheEmailIsSent() throws Exception {
        Users user = new Users();
        when(userService.findByEmail("anna@alfa.test")).thenReturn(Optional.of(user));
        when(tokenService.createToken(user)).thenReturn("plain-token");
        CountDownLatch mailServerAnswers = new CountDownLatch(1);
        CountDownLatch sent = new CountDownLatch(1);
        AtomicReference<String> thread = new AtomicReference<>();
        doAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            mailServerAnswers.await(5, TimeUnit.SECONDS);
            sent.countDown();
            return null;
        }).when(emailService).sendResetPasswordEmail(anyString(), anyString());

        PasswordResetMailer mailer = new PasswordResetMailer(userService, tokenService, emailService, FRONTEND);
        try {
            mailer.requestReset("anna@alfa.test");
            // Back here while the "mail server" is still holding the send.
            assertThat(sent.getCount()).isEqualTo(1);

            mailServerAnswers.countDown();
            assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(thread.get()).isEqualTo("password-reset-mail");
        } finally {
            mailServerAnswers.countDown();
            mailer.shutdown();
        }
    }
}
