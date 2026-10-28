/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.auth.AuthResponseDTO;
import com.donatodev.bcm_backend.dto.OrganizationRegistrationRequest;
import com.donatodev.bcm_backend.dto.RegistrationStartedDTO;
import com.donatodev.bcm_backend.dto.RequestOrigin;
import com.donatodev.bcm_backend.entity.PendingRegistration;
import com.donatodev.bcm_backend.exception.RegistrationCodeException;
import com.donatodev.bcm_backend.repository.PendingRegistrationRepository;

/**
 * Two-step self-service registration. Requesting it stores the data as a
 * {@link PendingRegistration} and emails a 6-digit code; typing the code back
 * creates the organization. The code both verifies the email address and is
 * the simple electronic signature of the onerous-clauses approval: after
 * Cass. ord. 20945/2026 a checkbox alone does not satisfy art. 1341 c.c.
 */
@Service
public class OrganizationRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationRegistrationService.class);

    static final int CODE_VALIDITY_MINUTES = 15;
    static final int RESEND_COOLDOWN_SECONDS = 60;
    static final int MAX_CODES_SENT = 5;
    static final int MAX_FAILED_ATTEMPTS = 5;

    static final String REGISTRATION_EXPIRED =
            "La registrazione è scaduta o non è più valida: compila di nuovo il modulo.";
    static final String WRONG_CODE = "Codice non corretto.";
    static final String TOO_MANY_ATTEMPTS =
            "Troppi tentativi con un codice errato: compila di nuovo il modulo.";

    private static final int CODE_BOUND = 1_000_000;

    private final PendingRegistrationRepository pendingRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;
    private final IEmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public OrganizationRegistrationService(
            PendingRegistrationRepository pendingRepository,
            OrganizationService organizationService,
            PasswordEncoder passwordEncoder,
            IEmailService emailService) {
        this.pendingRepository = pendingRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Validates the request, replaces any earlier pending attempt for the same
     * email, and sends the code. Sending happens inside the transaction, so a
     * mail failure leaves nothing behind.
     */
    @Transactional
    public RegistrationStartedDTO start(OrganizationRegistrationRequest request, RequestOrigin origin) {
        organizationService.validateNewRegistration(
                request.adminUsername(), request.adminEmail(), request.termsVersion());
        pendingRepository.deleteByAdminEmail(request.adminEmail());

        String publicId = UUID.randomUUID().toString();
        String code = newCode();
        LocalDateTime now = now();
        pendingRepository.save(PendingRegistration.builder()
                .publicId(publicId)
                .organizationName(request.organizationName())
                .adminFirstName(request.adminFirstName())
                .adminLastName(request.adminLastName())
                .adminEmail(request.adminEmail())
                .adminUsername(request.adminUsername())
                .passwordHash(passwordEncoder.encode(request.adminPassword()))
                .termsVersion(request.termsVersion())
                .codeHash(hash(publicId, code))
                .codeSentAt(now)
                .expiresAt(now.plusMinutes(CODE_VALIDITY_MINUTES))
                .requestIp(origin.ip())
                .requestUserAgent(origin.userAgent())
                .build());

        emailService.sendRegistrationCode(request.adminEmail(), code, CODE_VALIDITY_MINUTES);
        return started(publicId);
    }

    /**
     * Sends a fresh code, invalidating the previous one. Limited by a cooldown
     * and a maximum number of sends, so it can't be used to flood a mailbox.
     */
    @Transactional(noRollbackFor = RegistrationCodeException.class)
    public RegistrationStartedDTO resend(String registrationId) {
        PendingRegistration pending = findLive(registrationId);
        LocalDateTime now = now();
        if (pending.getCodesSent() >= MAX_CODES_SENT) {
            throw new IllegalArgumentException(
                    "Hai richiesto troppi codici: compila di nuovo il modulo.");
        }
        if (pending.getCodeSentAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            throw new IllegalArgumentException(
                    "Attendi qualche secondo prima di richiedere un nuovo codice.");
        }

        String code = newCode();
        pending.setCodeHash(hash(pending.getPublicId(), code));
        pending.setCodesSent(pending.getCodesSent() + 1);
        pending.setFailedAttempts(0);
        pending.setCodeSentAt(now);
        pending.setExpiresAt(now.plusMinutes(CODE_VALIDITY_MINUTES));
        pendingRepository.save(pending);

        emailService.sendRegistrationCode(pending.getAdminEmail(), code, CODE_VALIDITY_MINUTES);
        return started(pending.getPublicId());
    }

    /**
     * Checks the code and, if right, creates the organization. A wrong code is
     * counted and committed — hence {@code noRollbackFor} — and after
     * {@link #MAX_FAILED_ATTEMPTS} the pending registration is dropped, so the
     * million possible codes can't be guessed.
     */
    @Transactional(noRollbackFor = RegistrationCodeException.class)
    public AuthResponseDTO confirm(String registrationId, String code, RequestOrigin origin) {
        PendingRegistration pending = findLive(registrationId);

        boolean matches = MessageDigest.isEqual(
                hash(pending.getPublicId(), code).getBytes(StandardCharsets.US_ASCII),
                pending.getCodeHash().getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            int attempts = pending.getFailedAttempts() + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                pendingRepository.delete(pending);
                throw new RegistrationCodeException(TOO_MANY_ATTEMPTS);
            }
            pending.setFailedAttempts(attempts);
            pendingRepository.save(pending);
            throw new RegistrationCodeException(WRONG_CODE);
        }

        AuthResponseDTO tokens = organizationService.createConfirmedOrganization(pending, origin);
        pendingRepository.delete(pending);
        return tokens;
    }

    /** Hourly cleanup of registrations whose code expired without being used. */
    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = pendingRepository.deleteExpiredBefore(now());
        logger.info("Pending registration purge completed. {} expired entries deleted.", deleted);
    }

    private PendingRegistration findLive(String registrationId) {
        PendingRegistration pending = pendingRepository.findByPublicId(registrationId)
                .orElseThrow(() -> new RegistrationCodeException(REGISTRATION_EXPIRED));
        if (pending.getExpiresAt().isBefore(now())) {
            pendingRepository.delete(pending);
            throw new RegistrationCodeException(REGISTRATION_EXPIRED);
        }
        return pending;
    }

    private String newCode() {
        return String.format("%06d", random.nextInt(CODE_BOUND));
    }

    private static RegistrationStartedDTO started(String publicId) {
        return new RegistrationStartedDTO(publicId, CODE_VALIDITY_MINUTES, RESEND_COOLDOWN_SECONDS);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    /** Salted with the registration id, so equal codes never share a hash. */
    static String hash(String publicId, String code) {
        return DigestUtils.sha256Hex(publicId + ":" + code);
    }
}
