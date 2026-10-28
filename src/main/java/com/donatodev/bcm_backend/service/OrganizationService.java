/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.auth.AuthResponseDTO;
import com.donatodev.bcm_backend.config.LegalTerms;
import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.OrganizationDTO;
import com.donatodev.bcm_backend.dto.OrganizationUsageDTO;
import com.donatodev.bcm_backend.dto.RequestOrigin;
import com.donatodev.bcm_backend.dto.UpdateOrganizationRequest;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.PendingRegistration;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.TermsAcceptance;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.TermsAcceptanceRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.IbanValidator;

@Service
public class OrganizationService {

    private static final String ORGANIZATION_NOT_FOUND = "Organizzazione non trovata";

    private final OrganizationRepository organizationRepository;
    private final UsersRepository usersRepository;
    private final ManagersRepository managersRepository;
    private final RolesRepository rolesRepository;
    private final TermsAcceptanceRepository termsAcceptanceRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final ContractsRepository contractsRepository;
    private final TierLimitService tierLimitService;
    private final AuditLogService auditLogService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            UsersRepository usersRepository,
            ManagersRepository managersRepository,
            RolesRepository rolesRepository,
            TermsAcceptanceRepository termsAcceptanceRepository,
            JwtUtils jwtUtils,
            RefreshTokenService refreshTokenService,
            ContractsRepository contractsRepository,
            TierLimitService tierLimitService,
            AuditLogService auditLogService) {
        this.organizationRepository = organizationRepository;
        this.usersRepository = usersRepository;
        this.managersRepository = managersRepository;
        this.rolesRepository = rolesRepository;
        this.termsAcceptanceRepository = termsAcceptanceRepository;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.contractsRepository = contractsRepository;
        this.tierLimitService = tierLimitService;
        this.auditLogService = auditLogService;
    }

    /**
     * Checks a registration can go ahead. Run when it is requested and again
     * when it is confirmed, since the username or email may have been taken
     * in between.
     */
    @Transactional(readOnly = true)
    public void validateNewRegistration(String username, String email, String termsVersion) {
        if (usersRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username già esistente.");
        }
        if (managersRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email già in uso.");
        }
        // A stale page (Terms changed since it was loaded) must not create an
        // acceptance pointing at a text the user never saw.
        if (!LegalTerms.CURRENT_VERSION.equals(termsVersion)) {
            throw new IllegalArgumentException(
                    "I Termini di servizio sono stati aggiornati: ricarica la pagina e accettali di nuovo.");
        }
    }

    /**
     * Creates the organization and its admin once the emailed code has been
     * typed back, and stores the signed Terms acceptance as evidence. The
     * email is verified by then, hence {@code verified(true)}.
     *
     * @param confirmOrigin where the confirming request came from
     */
    @Transactional
    public AuthResponseDTO createConfirmedOrganization(PendingRegistration pending, RequestOrigin confirmOrigin) {
        validateNewRegistration(pending.getAdminUsername(), pending.getAdminEmail(), pending.getTermsVersion());

        LocalDateTime acceptedAt = LocalDateTime.now(ZoneId.systemDefault());
        Organization org = organizationRepository.save(Organization.builder()
                .name(pending.getOrganizationName())
                .slug(generateUniqueSlug(pending.getOrganizationName()))
                .subscriptionTier(SubscriptionTier.FREE)
                .termsVersion(pending.getTermsVersion())
                .termsAcceptedAt(acceptedAt)
                .build());

        Managers manager = managersRepository.save(Managers.builder()
                .firstName(pending.getAdminFirstName())
                .lastName(pending.getAdminLastName())
                .email(pending.getAdminEmail())
                .organization(org)
                .build());

        Roles adminRole = rolesRepository.findByRole("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Ruolo ADMIN non trovato nel database"));

        Users admin = usersRepository.save(Users.builder()
                .username(pending.getAdminUsername())
                .passwordHash(pending.getPasswordHash())
                .role(adminRole)
                .manager(manager)
                .organization(org)
                .verified(true)
                .build());

        termsAcceptanceRepository.save(TermsAcceptance.builder()
                .organizationId(org.getId())
                .organizationName(org.getName())
                .userId(admin.getId())
                .username(admin.getUsername())
                .signerEmail(pending.getAdminEmail())
                .termsVersion(pending.getTermsVersion())
                .onerousClauses(LegalTerms.ONEROUS_CLAUSES)
                .professionalUseDeclared(true)
                .signatureMethod(TermsAcceptance.SIGNATURE_EMAIL_OTP)
                .codeSentAt(pending.getCodeSentAt())
                .acceptedAt(acceptedAt)
                .requestIp(pending.getRequestIp())
                .requestUserAgent(pending.getRequestUserAgent())
                .confirmIp(confirmOrigin.ip())
                .confirmUserAgent(confirmOrigin.userAgent())
                .build());

        auditLogService.save("TERMS_ACCEPTED", "Organization", org.getId(), admin.getUsername(), org.getId(),
                "terms " + pending.getTermsVersion() + "; professional use declared; onerous clauses "
                + LegalTerms.ONEROUS_CLAUSES + " approved (artt. 1341-1342 c.c.); signed with emailed code");

        String accessToken = jwtUtils.generateToken(admin);
        String refreshToken = refreshTokenService.createRefreshToken(admin);
        return new AuthResponseDTO(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public OrganizationDTO getMyOrganization() {
        Organization org = resolveCurrentOrganization();
        return toDTO(org);
    }

    /**
     * Current contract count against the organization's tier limit, for
     * display (e.g. "8 / 15 contracts") — same numbers
     * {@code TierLimitService#enforceContractCreationLimit} enforces, read
     * here rather than duplicated.
     */
    @Transactional(readOnly = true)
    public OrganizationUsageDTO getUsage() {
        Organization org = resolveCurrentOrganization();
        int used = contractsRepository.countAllContractsByOrg(org.getId());
        Integer limit = tierLimitService.getContractLimit(org.getSubscriptionTier());
        return new OrganizationUsageDTO(used, limit);
    }

    @Transactional
    public OrganizationDTO updateMyOrganization(UpdateOrganizationRequest request) {
        Organization org = resolveCurrentOrganization();

        if (request.name() != null && !request.name().isBlank()) {
            org.setName(request.name());
        }
        if (request.iban() != null) {
            String normalizedIban = request.iban().replace(" ", "").toUpperCase(Locale.ROOT);
            if (!normalizedIban.isEmpty() && !IbanValidator.isValid(normalizedIban)) {
                throw new IllegalArgumentException("IBAN non valido");
            }
            org.setIban(normalizedIban.isEmpty() ? null : normalizedIban);
        }
        if (request.bic() != null) {
            String normalizedBic = request.bic().replace(" ", "").toUpperCase(Locale.ROOT);
            org.setBic(normalizedBic.isEmpty() ? null : normalizedBic);
        }

        return toDTO(organizationRepository.save(org));
    }

    /**
     * The single place an organization's subscription tier changes — called by
     * the internal ops endpoint today, meant for the billing webhook next, so
     * both paths share the same audit trail. Deliberately takes an explicit
     * orgId instead of reading {@link TenantContext}: the caller is never a
     * tenant user (see {@code UpdateOrganizationRequest} for why tier is not
     * self-service). A downgrade does not touch existing contracts — tier
     * limits are only enforced on creation ({@code TierLimitService}).
     *
     * @param source who triggered the change, recorded in the audit log (e.g. "manual", "stripe")
     */
    @Transactional
    public OrganizationDTO changeTier(Long orgId, SubscriptionTier tier, String source) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException(ORGANIZATION_NOT_FOUND));
        SubscriptionTier previous = org.getSubscriptionTier();
        if (previous == tier) {
            return toDTO(org);
        }
        org.setSubscriptionTier(tier);
        Organization saved = organizationRepository.save(org);
        auditLogService.save("TIER_CHANGED", "Organization", orgId, "system:" + source, orgId,
                previous + " -> " + tier);
        return toDTO(saved);
    }

    /**
     * Records the Stripe customer behind an org's subscription (from a
     * completed checkout). A no-op when it's already the stored one, so a
     * redelivered webhook writes nothing.
     */
    @Transactional
    public void linkStripeCustomer(Long orgId, String stripeCustomerId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException(ORGANIZATION_NOT_FOUND));
        if (stripeCustomerId.equals(org.getStripeCustomerId())) {
            return;
        }
        org.setStripeCustomerId(stripeCustomerId);
        organizationRepository.save(org);
    }

    private Organization resolveCurrentOrganization() {
        Long orgId = TenantContext.get();
        if (orgId != null) {
            return organizationRepository.findById(orgId)
                    .orElseThrow(() -> new OrganizationNotFoundException(ORGANIZATION_NOT_FOUND));
        }
        // Fallback when TenantContext is absent (e.g. test context with @WithMockUser)
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return usersRepository.findByUsername(username)
                .map(u -> {
                    if (u.getOrganization() == null) {
                        throw new OrganizationNotFoundException("L'utente non ha un'organizzazione");
                    }
                    return u.getOrganization();
                })
                .orElseThrow(() -> new OrganizationNotFoundException("Utente autenticato non trovato"));
    }

    private String generateUniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");

        if (base.isEmpty()) {
            base = "org";
        }

        String slug = base;
        if (organizationRepository.findBySlug(slug).isPresent()) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }

    private OrganizationDTO toDTO(Organization org) {
        return new OrganizationDTO(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getSubscriptionTier(),
                IbanValidator.maskNullable(org.getIban()),
                IbanValidator.maskNullable(org.getBic()),
                org.getCreatedAt(),
                org.getStripeCustomerId() != null);
    }
}
