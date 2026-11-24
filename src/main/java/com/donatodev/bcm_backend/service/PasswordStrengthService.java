/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.security.PwnedPasswordsClient;
import com.donatodev.bcm_backend.validation.PasswordPolicy;

/**
 * Every password set anywhere passes here (OWASP ASVS 6.2): at least 8
 * characters and no composition rules (PasswordPolicy), and then not one of
 * the most used (6.2.4: the NCSC's list, 46,000 of 8 characters or more,
 * case-insensitive), not built on the account's own words (6.2.11: its name,
 * its organization's, the application's), and not already leaked (6.2.12,
 * Have I Been Pwned, by k-anonymity; skipped when the service does not
 * answer). Documented in docs/BUSINESS_RULES.md.
 * <p>
 * The public demo account is left out: its password is printed on the login
 * page on purpose, so how guessable it is protects nothing, and the nightly
 * demo reset sets it again through the API.
 */
@Service
public class PasswordStrengthService {

    static final String COMMON = "Questa password è tra le più usate: scegline una meno prevedibile";
    static final String CONTEXT =
            "La password non deve contenere il nome utente, il nome dell'organizzazione o il nome dell'applicazione";
    static final String BREACHED =
            "Questa password compare in violazioni di dati già note: scegline un'altra";

    /** Words of the application itself that a password must not be built on. */
    private static final Set<String> APPLICATION_WORDS = Set.of("bcm");
    /** Account words shorter than this are too common in any password to refuse them. */
    static final int MIN_CONTEXT_WORD = 4;

    /** Loaded once, when the class is: a missing list stops the application at start. */
    private static final Set<String> COMMON_PASSWORDS = load("/security/common-passwords.txt");

    private final PwnedPasswordsClient pwned;
    private final String publicDemoUsername;

    public PasswordStrengthService(PwnedPasswordsClient pwned,
            @Value("${bcm.demo.locked-username:}") String publicDemoUsername) {
        this.pwned = pwned;
        this.publicDemoUsername = publicDemoUsername == null ? "" : publicDemoUsername.trim();
    }

    /**
     * Refuses a password for the account {@code username} with the Italian
     * reason of the first rule it breaks.
     *
     * @param context more words of the account (its organization's name, its
     *                person's names and email): split on spaces and dots
     * @throws IllegalArgumentException with the reason, which reaches the form as a 400
     */
    public void require(String password, String username, String... context) {
        PasswordPolicy.require(password);
        if (!publicDemoUsername.isEmpty() && publicDemoUsername.equals(username)) {
            return;
        }
        // Only to compare with the list and the account's words, never stored.
        String lower = Normalizer.normalize(password, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(lower)) {
            throw new IllegalArgumentException(COMMON);
        }
        if (containsAccountWord(lower, username, context)) {
            throw new IllegalArgumentException(CONTEXT);
        }
        if (pwned.isBreached(password).orElse(false)) {
            throw new IllegalArgumentException(BREACHED);
        }
    }

    static boolean containsAccountWord(String lowerPassword, String username, String... context) {
        for (String word : APPLICATION_WORDS) {
            if (lowerPassword.contains(word)) {
                return true;
            }
        }
        Set<String> words = new HashSet<>();
        addWords(words, username);
        if (context != null) {
            for (String value : context) {
                addWords(words, value);
            }
        }
        return words.stream().anyMatch(lowerPassword::contains);
    }

    private static void addWords(Set<String> words, String value) {
        if (value == null) {
            return;
        }
        for (String part : value.toLowerCase(Locale.ROOT).split("[\\s.@_\\-]+")) {
            if (part.codePointCount(0, part.length()) >= MIN_CONTEXT_WORD) {
                words.add(part);
            }
        }
    }

    private static Set<String> load(String resource) {
        Set<String> passwords = new HashSet<>();
        InputStream in = PasswordStrengthService.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    passwords.add(line.trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Set.copyOf(passwords);
    }
}
