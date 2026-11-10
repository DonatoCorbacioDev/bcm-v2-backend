package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;

import com.donatodev.bcm_backend.entity.PasswordResetToken;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.PasswordResetTokenRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@DisplayName("Unit Test: SessionRevocationService")
class SessionRevocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:30.700Z");

    private RefreshTokenRepository refreshTokenRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private UsersRepository usersRepository;
    private SessionRevocationService service;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        usersRepository = mock(UsersRepository.class);
        service = new SessionRevocationService(refreshTokenRepository, passwordResetTokenRepository, usersRepository);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("records the change in whole seconds, revokes every refresh token and deletes a pending reset link")
    void endsEverySession() {
        Users user = Users.builder().id(uid(1)).username("alice").build();
        PasswordResetToken pending = PasswordResetToken.builder().token("pending").user(user).build();
        when(passwordResetTokenRepository.findByUser(user)).thenReturn(Optional.of(pending));

        service.credentialsChanged(user);

        assertEquals(NOW.getEpochSecond(), user.getCredentialsChangedAt());
        InOrder order = inOrder(usersRepository, refreshTokenRepository);
        order.verify(usersRepository).save(user);
        order.verify(refreshTokenRepository).revokeAllByUser(user);
        verify(passwordResetTokenRepository).delete(pending);
    }

    @Test
    @DisplayName("with no pending reset link there is nothing to delete")
    void noPendingResetLink() {
        Users user = Users.builder().id(uid(2)).username("bob").build();
        when(passwordResetTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        service.credentialsChanged(user);

        verify(refreshTokenRepository).revokeAllByUser(user);
        verify(passwordResetTokenRepository, never()).delete(ArgumentMatchers.any());
    }
}
