/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.donatodev.bcm_backend.security.PwnedPasswordsClient;
import com.donatodev.bcm_backend.validation.PasswordPolicy;

/** The rules of docs/BUSINESS_RULES.md for every password set (OWASP ASVS 6.2). */
class PasswordStrengthServiceTest {

    private static final String DEMO = "demo";

    private final PwnedPasswordsClient pwned = mock(PwnedPasswordsClient.class);
    private final PasswordStrengthService strength = new PasswordStrengthService(pwned, DEMO);

    @ParameterizedTest
    @ValueSource(strings = {"password", "Password1", "12345678", "QWERTYUIOP", "iloveyou"})
    @DisplayName("one of the most used passwords is refused, whatever its case")
    void common(String password) {
        assertThatThrownBy(() -> strength.require(password, "anna"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(PasswordStrengthService.COMMON);
    }

    @Test
    @DisplayName("a password built on the account's name, its organization or the application is refused")
    void context() {
        assertThatThrownBy(() -> strength.require("mrossi-Vesuvio-7", "mrossi"))
                .hasMessage(PasswordStrengthService.CONTEXT);
        assertThatThrownBy(() -> strength.require("Vesuvio-Nortiva-7", "anna", "Nortiva Solutions S.r.l."))
                .hasMessage(PasswordStrengthService.CONTEXT);
        assertThatThrownBy(() -> strength.require("Vesuvio-verdi-77", "anna", "Anna", "Verdi", "a.verdi@studio.example"))
                .hasMessage(PasswordStrengthService.CONTEXT);
        assertThatThrownBy(() -> strength.require("Vesuvio-BCM-2026", "anna"))
                .hasMessage(PasswordStrengthService.CONTEXT);
    }

    @Test
    @DisplayName("words of fewer than four letters are no reason to refuse: they are in too many passwords")
    void shortWordsIgnored() {
        assertThatCode(() -> strength.require("Ana-Vesuvio-Lungomare", "ana", "Ana", "Re"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a password Have I Been Pwned knows as leaked is refused")
    void breached() {
        when(pwned.isBreached("Vesuvio-Lungomare-41")).thenReturn(Optional.of(true));
        assertThatThrownBy(() -> strength.require("Vesuvio-Lungomare-41", "anna"))
                .hasMessage(PasswordStrengthService.BREACHED);
    }

    @Test
    @DisplayName("when Have I Been Pwned cannot answer, the local rules decide alone")
    void breachCheckUnavailable() {
        when(pwned.isBreached(anyString())).thenReturn(Optional.empty());
        assertThatCode(() -> strength.require("Vesuvio-Lungomare-41", "anna")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the length rule still comes first")
    void lengthFirst() {
        assertThatThrownBy(() -> strength.require("short", "anna")).hasMessage(PasswordPolicy.TOO_SHORT);
        verify(pwned, never()).isBreached(anyString());
    }

    @Test
    @DisplayName("the public demo account is left out: its password is on the login page on purpose")
    void publicDemoLeftOut() {
        assertThatCode(() -> strength.require("BcmDemo#2026", DEMO)).doesNotThrowAnyException();
        verify(pwned, never()).isBreached(anyString());
    }

    @Test
    @DisplayName("a password that breaks none of the rules is accepted")
    void strong() {
        when(pwned.isBreached(anyString())).thenReturn(Optional.of(false));
        assertThatCode(() -> strength.require("Vesuvio-Lungomare-41", "anna", "Nortiva Solutions"))
                .doesNotThrowAnyException();
    }
}
