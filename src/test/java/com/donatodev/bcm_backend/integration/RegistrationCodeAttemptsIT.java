/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.donatodev.bcm_backend.dto.RequestOrigin;
import com.donatodev.bcm_backend.entity.PendingRegistration;
import com.donatodev.bcm_backend.exception.RegistrationCodeException;
import com.donatodev.bcm_backend.repository.PendingRegistrationRepository;
import com.donatodev.bcm_backend.service.OrganizationRegistrationService;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * The registration code is checked at most five times, however many wrong
 * guesses arrive at once (security audit 2026-11-24): the confirmations of one
 * registration lock its row, so none of them reads a count another is about to
 * raise. Real MySQL, because the protection is InnoDB's row lock. The anonymous
 * sign-up is the path that matters: a right guess creates an organization and
 * its administrator for an address the guesser does not own.
 */
@SpringBootTest
class RegistrationCodeAttemptsIT extends AbstractMySQLIntegrationTest {

    private static final int SIMULTANEOUS_GUESSES = 20;
    private static final String WRONG_CODE = "Codice non corretto.";

    @Autowired private OrganizationRegistrationService registrationService;
    @Autowired private PendingRegistrationRepository pendingRepository;
    @Autowired private JdbcTemplate jdbc;

    private String publicId;

    @BeforeEach
    void pendingRegistration() {
        publicId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        pendingRepository.save(PendingRegistration.builder()
                .publicId(publicId).organizationName("Guessed Srl")
                .adminFirstName("A").adminLastName("B").adminEmail(publicId + "@example.com")
                .adminUsername("guess-" + publicId.substring(0, 8)).passwordHash("not-a-real-hash")
                .termsVersion("2026-10-28")
                // No code hashes to this: every guess below is wrong.
                .codeHash("0".repeat(64))
                .codeSentAt(now).expiresAt(now.plusMinutes(15))
                .build());
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM pending_registrations WHERE public_id = ?", publicId);
    }

    @Test
    @DisplayName("twenty simultaneous wrong codes: four answered 'wrong', then the registration is closed")
    void wrongGuessesAreAllCounted() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(SIMULTANEOUS_GUESSES);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> answers = new ArrayList<>();
        try {
            for (int i = 0; i < SIMULTANEOUS_GUESSES; i++) {
                String guess = String.format("%06d", 100_000 + i);
                answers.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        registrationService.confirm(publicId, guess, new RequestOrigin("203.0.113.9", "it"));
                        return "CONFIRMED";
                    } catch (RegistrationCodeException e) {
                        return e.getMessage();
                    }
                }));
            }
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> answer : answers) {
                results.add(answer.get(60, TimeUnit.SECONDS));
            }

            // At most MAX_FAILED_ATTEMPTS - 1 guesses are told "wrong"; the fifth
            // closes the registration and every later one finds it closed.
            assertThat(results).doesNotContain("CONFIRMED");
            assertThat(results.stream().filter(WRONG_CODE::equals).count()).isEqualTo(4);
        } finally {
            pool.shutdownNow();
        }
        Integer attempts = jdbc.queryForObject(
                "SELECT failed_attempts FROM pending_registrations WHERE public_id = ?", Integer.class, publicId);
        assertThat(attempts).isEqualTo(5);
    }
}
