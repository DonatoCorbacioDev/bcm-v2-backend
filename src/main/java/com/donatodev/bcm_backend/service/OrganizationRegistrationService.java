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

import com.donatodev.bcm_backend.auth.LoginOutcome;
import com.donatodev.bcm_backend.auth.SessionIssuer;
import com.donatodev.bcm_backend.dto.OrganizationRegistrationRequest;
import com.donatodev.bcm_backend.dto.RegistrationStartedDTO;
import com.donatodev.bcm_backend.dto.RequestOrigin;
import com.donatodev.bcm_backend.entity.PendingRegistration;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.RegistrationCodeException;
import com.donatodev.bcm_backend.repository.PendingRegistrationRepository;
import com.donatodev.bcm_backend.security.SecurityEvents;
import com.donatodev.bcm_backend.validation.PasswordPolicy;

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
    /** The codes an address can receive are counted over this many hours, across new attempts. */
    static final int SEND_WINDOW_HOURS = 24;

    static final String WAIT_BEFORE_NEW_CODE = "Attendi qualche secondo prima di richiedere un nuovo codice.";
    static final String TOO_MANY_CODES =
            "Hai richiesto troppi codici per questo indirizzo email: riprova domani.";

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
    private final SessionIssuer sessionIssuer;
    private final SecurityEvents securityEvents;
    private final SecureRandom random = new SecureRandom();

    public OrganizationRegistrationService(
            PendingRegistrationRepository pendingRepository,
            OrganizationService organizationService,
            PasswordEncoder passwordEncoder,
            IEmailService emailService,
            SessionIssuer sessionIssuer,
            SecurityEvents securityEvents) {
        this.securityEvents = securityEvents;
        this.pendingRepository = pendingRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.sessionIssuer = sessionIssuer;
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
        PasswordPolicy.require(request.adminPassword());
        LocalDateTime now = now();
        // Starting over is no way around the limits of resend (OWASP API6): the
        // count of codes the address received carries over to the new attempt.
        int codesSent = pendingRepository.findFirstByAdminEmailOrderByCodeSentAtDesc(request.adminEmail())
                .map(previous -> codesAlreadySent(previous, now))
                .orElse(0);
        pendingRepository.deleteByAdminEmail(request.adminEmail());

        String publicId = UUID.randomUUID().toString();
        String code = newCode();
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
                .codesSent(codesSent + 1)
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
        int codesSent = codesAlreadySent(pending, now);

        String code = newCode();
        pending.setCodeHash(hash(pending.getPublicId(), code));
        pending.setCodesSent(codesSent + 1);
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
    public LoginOutcome confirm(String registrationId, String code, RequestOrigin origin) {
        PendingRegistration pending = findLive(registrationId);

        boolean matches = MessageDigest.isEqual(
                hash(pending.getPublicId(), code).getBytes(StandardCharsets.US_ASCII),
                pending.getCodeHash().getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            int attempts = pending.getFailedAttempts() + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                // Closed, not deleted: the row keeps the count of codes sent to the address.
                pending.setFailedAttempts(attempts);
                pending.setExpiresAt(now());
                pendingRepository.save(pending);
                throw new RegistrationCodeException(TOO_MANY_ATTEMPTS);
            }
            pending.setFailedAttempts(attempts);
            pendingRepository.save(pending);
            throw new RegistrationCodeException(WRONG_CODE);
        }

        Users admin = organizationService.createConfirmedOrganization(pending, origin);
        pendingRepository.delete(pending);
        // The new admin sets 2FA up before the first session (TwoFactorPolicy).
        return sessionIssuer.afterFirstFactor(admin);
    }

    /**
     * Hourly cleanup of registrations whose code expired without being used,
     * once a day has passed: until then the row is what limits the codes the
     * address can receive.
     */
    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = pendingRepository.deleteExpiredBefore(now().minusHours(SEND_WINDOW_HOURS));
        logger.info("Pending registration purge completed. {} expired entries deleted.", deleted);
    }

    /**
     * How many codes the address has received in the current window, refusing
     * one more if it asked within the cooldown or has had the most a day
     * allows. Each refusal is recorded for the weekly report.
     */
    private int codesAlreadySent(PendingRegistration previous, LocalDateTime now) {
        if (previous.getCodeSentAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            securityEvents.record(SecurityEvents.Type.REGISTRATION_CODE_REFUSED, "cooldown");
            throw new IllegalArgumentException(WAIT_BEFORE_NEW_CODE);
        }
        if (previous.getCodeSentAt().plusHours(SEND_WINDOW_HOURS).isBefore(now)) {
            return 0;
        }
        if (previous.getCodesSent() >= MAX_CODES_SENT) {
            securityEvents.record(SecurityEvents.Type.REGISTRATION_CODE_REFUSED, "daily limit");
            throw new IllegalArgumentException(TOO_MANY_CODES);
        }
        return previous.getCodesSent();
    }

    /** An expired attempt stays until the purge: deleting it here would reset its count of codes. */
    private PendingRegistration findLive(String registrationId) {
        PendingRegistration pending = pendingRepository.findByPublicId(registrationId)
                .orElseThrow(() -> new RegistrationCodeException(REGISTRATION_EXPIRED));
        if (pending.getExpiresAt().isBefore(now())) {
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
