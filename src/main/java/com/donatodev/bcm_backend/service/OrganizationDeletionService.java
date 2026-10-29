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
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.dto.DeletionCodeSentDTO;
import com.donatodev.bcm_backend.dto.OrganizationDeletionDTO;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.OrganizationDeletionCode;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.DeletionCodeException;
import com.donatodev.bcm_backend.exception.OrganizationDeletionConflictException;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.repository.OrganizationDeletionCodeRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Self-service deletion of an organization (GDPR art. 17, Terms sez. 13),
 * in three steps:
 * <ol>
 * <li>{@link #request}: the admin gives their password and retypes the
 * organization name, and gets a code by email;</li>
 * <li>{@link #confirm}: the code schedules the deletion {@value #GRACE_DAYS}
 * days ahead. Until then everything keeps working, so the data can still be
 * exported, and every admin is told by email;</li>
 * <li>{@link #cancel}: any admin can call it off before that day.</li>
 * </ol>
 * Three factors because the step can't be undone once it runs: the password
 * stops a stolen session, the name a click by mistake, the code someone who
 * only knows the password.
 */
@Service
public class OrganizationDeletionService {

    static final int GRACE_DAYS = 30;
    static final int CODE_VALIDITY_MINUTES = 15;
    static final int RESEND_COOLDOWN_SECONDS = 60;
    static final int MAX_FAILED_ATTEMPTS = 5;

    static final String WRONG_PASSWORD = "Password non corretta.";
    static final String WRONG_NAME = "Il nome dell'organizzazione non corrisponde.";
    static final String ALREADY_SCHEDULED = "La cancellazione dell'organizzazione è già programmata.";
    static final String PAID_SUBSCRIPTION =
            "Prima annulla l'abbonamento da \"Gestisci abbonamento\": poi potrai cancellare l'organizzazione.";
    static final String CODE_EXPIRED = "Il codice è scaduto o non è più valido: ripeti la richiesta.";
    static final String WRONG_CODE = "Codice non corretto.";
    static final String TOO_MANY_ATTEMPTS = "Troppi tentativi con un codice errato: ripeti la richiesta.";
    static final String RESEND_TOO_SOON = "Attendi qualche secondo prima di richiedere un nuovo codice.";
    static final String NOTHING_SCHEDULED = "Non c'è nessuna cancellazione programmata.";

    private static final int CODE_BOUND = 1_000_000;

    private final OrganizationRepository organizationRepository;
    private final OrganizationDeletionCodeRepository codeRepository;
    private final UsersRepository usersRepository;
    private final CurrentUserResolver currentUserResolver;
    private final PasswordEncoder passwordEncoder;
    private final IEmailService emailService;
    private final AuditLogService auditLogService;
    private final SecureRandom random = new SecureRandom();

    @SuppressWarnings("java:S107") // one collaborator per concern; grouping them would only hide that
    public OrganizationDeletionService(OrganizationRepository organizationRepository,
            OrganizationDeletionCodeRepository codeRepository, UsersRepository usersRepository,
            CurrentUserResolver currentUserResolver, PasswordEncoder passwordEncoder,
            IEmailService emailService, AuditLogService auditLogService) {
        this.organizationRepository = organizationRepository;
        this.codeRepository = codeRepository;
        this.usersRepository = usersRepository;
        this.currentUserResolver = currentUserResolver;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    /**
     * Checks password and name and emails a code to the requesting admin.
     * Asking again replaces the previous code, after a short cooldown so it
     * can't be used to flood the mailbox.
     */
    @Transactional
    public DeletionCodeSentDTO request(String password, String organizationName) {
        Users admin = currentUserResolver.resolve();
        Organization org = organizationOf(admin);
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new IllegalArgumentException(WRONG_PASSWORD);
        }
        if (!org.getName().trim().equals(organizationName.trim())) {
            throw new IllegalArgumentException(WRONG_NAME);
        }
        ensureDeletable(org);
        String email = emailOf(admin);

        LocalDateTime now = now();
        // Updated in place rather than deleted and re-inserted: Hibernate
        // flushes inserts before deletes, which would trip the unique key.
        OrganizationDeletionCode pending = codeRepository.findByOrganizationId(org.getId())
                .orElseGet(() -> OrganizationDeletionCode.builder().organizationId(org.getId()).build());
        if (pending.getCodeSentAt() != null
                && pending.getCodeSentAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            throw new IllegalArgumentException(RESEND_TOO_SOON);
        }
        String code = String.format("%06d", random.nextInt(CODE_BOUND));
        pending.setUserId(admin.getId());
        pending.setCodeHash(hash(org.getId(), code));
        pending.setFailedAttempts(0);
        pending.setCodeSentAt(now);
        pending.setExpiresAt(now.plusMinutes(CODE_VALIDITY_MINUTES));
        codeRepository.save(pending);

        emailService.sendOrganizationDeletionCode(email, org.getName(), code, CODE_VALIDITY_MINUTES);
        return new DeletionCodeSentDTO(CODE_VALIDITY_MINUTES, RESEND_COOLDOWN_SECONDS);
    }

    /**
     * Checks the code and schedules the deletion. A wrong code is counted and
     * committed ({@code noRollbackFor}); after {@link #MAX_FAILED_ATTEMPTS}
     * the code is dropped and the request must start over. Only the admin who
     * asked can confirm.
     */
    @Transactional(noRollbackFor = DeletionCodeException.class)
    public OrganizationDeletionDTO confirm(String code) {
        Users admin = currentUserResolver.resolve();
        Organization org = organizationOf(admin);
        OrganizationDeletionCode pending = codeRepository.findByOrganizationId(org.getId())
                .filter(c -> c.getUserId().equals(admin.getId()))
                .orElseThrow(() -> new DeletionCodeException(CODE_EXPIRED));
        if (pending.getExpiresAt().isBefore(now())) {
            codeRepository.delete(pending);
            throw new DeletionCodeException(CODE_EXPIRED);
        }
        boolean matches = MessageDigest.isEqual(
                hash(org.getId(), code).getBytes(StandardCharsets.US_ASCII),
                pending.getCodeHash().getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            int attempts = pending.getFailedAttempts() + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                codeRepository.delete(pending);
                throw new DeletionCodeException(TOO_MANY_ATTEMPTS);
            }
            pending.setFailedAttempts(attempts);
            codeRepository.save(pending);
            throw new DeletionCodeException(WRONG_CODE);
        }
        codeRepository.delete(pending);
        // Checked again: the state may have changed since the code was sent.
        ensureDeletable(org);

        LocalDateTime now = now();
        org.setDeletionScheduledFor(now.plusDays(GRACE_DAYS));
        org.setDeletionRequestedAt(now);
        org.setDeletionRequestedBy(admin.getUsername());
        organizationRepository.save(org);
        auditLogService.save("ORGANIZATION_DELETION_SCHEDULED", "Organization", org.getId(),
                admin.getUsername(), org.getId(), "Deletion scheduled for " + org.getDeletionScheduledFor().toLocalDate());

        // Every admin hears about it, not just the one who asked: if that
        // account was misused, the others can still cancel in time.
        for (String email : adminEmails(org)) {
            emailService.sendOrganizationDeletionScheduled(email, org.getName(), admin.getUsername(),
                    org.getDeletionScheduledFor().toLocalDate());
        }
        return new OrganizationDeletionDTO(org.getDeletionScheduledFor());
    }

    /** Calls off a scheduled deletion. Any admin of the organization can. */
    @Transactional
    public void cancel() {
        Users admin = currentUserResolver.resolve();
        Organization org = organizationOf(admin);
        if (org.getDeletionScheduledFor() == null) {
            throw new IllegalArgumentException(NOTHING_SCHEDULED);
        }
        org.setDeletionScheduledFor(null);
        org.setDeletionRequestedAt(null);
        org.setDeletionRequestedBy(null);
        organizationRepository.save(org);
        auditLogService.save("ORGANIZATION_DELETION_CANCELLED", "Organization", org.getId(),
                admin.getUsername(), org.getId(), "Scheduled deletion cancelled");
        for (String email : adminEmails(org)) {
            emailService.sendOrganizationDeletionCancelled(email, org.getName(), admin.getUsername());
        }
    }

    private static void ensureDeletable(Organization org) {
        if (org.getDeletionScheduledFor() != null) {
            throw new OrganizationDeletionConflictException(ALREADY_SCHEDULED);
        }
        // A plan granted by hand has no Stripe customer and nothing to cancel.
        if (org.getSubscriptionTier() != SubscriptionTier.FREE && org.getStripeCustomerId() != null) {
            throw new OrganizationDeletionConflictException(PAID_SUBSCRIPTION);
        }
    }

    private static Organization organizationOf(Users user) {
        if (user.getOrganization() == null) {
            throw new OrganizationNotFoundException("L'utente non ha un'organizzazione");
        }
        return user.getOrganization();
    }

    private static String emailOf(Users user) {
        if (user.getManager() == null || user.getManager().getEmail() == null) {
            throw new IllegalArgumentException(
                    "Al tuo account non è associato un indirizzo email: impossibile inviare il codice.");
        }
        return user.getManager().getEmail();
    }

    private List<String> adminEmails(Organization org) {
        return usersRepository.findByOrganizationIdAndRoleRole(org.getId(), "ADMIN").stream()
                .filter(u -> u.getManager() != null && u.getManager().getEmail() != null)
                .map(u -> u.getManager().getEmail())
                .distinct()
                .toList();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    /** Prefixed so a deletion code can never collide with a registration code hash. */
    static String hash(Long orgId, String code) {
        return DigestUtils.sha256Hex("delete:" + orgId + ":" + code);
    }
}
