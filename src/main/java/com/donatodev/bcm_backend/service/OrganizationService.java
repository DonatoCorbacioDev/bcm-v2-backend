/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.auth.AuthResponseDTO;
import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.OrganizationDTO;
import com.donatodev.bcm_backend.dto.OrganizationRegistrationRequest;
import com.donatodev.bcm_backend.dto.OrganizationUsageDTO;
import com.donatodev.bcm_backend.dto.UpdateOrganizationRequest;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.IbanValidator;

@Service
public class OrganizationService {

    private static final String ORGANIZATION_NOT_FOUND = "Organizzazione non trovata";

    private final OrganizationRepository organizationRepository;
    private final UsersRepository usersRepository;
    private final ManagersRepository managersRepository;
    private final RolesRepository rolesRepository;
    private final PasswordEncoder passwordEncoder;
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
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            RefreshTokenService refreshTokenService,
            ContractsRepository contractsRepository,
            TierLimitService tierLimitService,
            AuditLogService auditLogService) {
        this.organizationRepository = organizationRepository;
        this.usersRepository = usersRepository;
        this.managersRepository = managersRepository;
        this.rolesRepository = rolesRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.contractsRepository = contractsRepository;
        this.tierLimitService = tierLimitService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AuthResponseDTO registerOrganization(OrganizationRegistrationRequest request) {
        if (usersRepository.existsByUsername(request.adminUsername())) {
            throw new IllegalArgumentException("Username già esistente.");
        }
        if (managersRepository.existsByEmail(request.adminEmail())) {
            throw new IllegalArgumentException("Email già in uso.");
        }

        String slug = generateUniqueSlug(request.organizationName());

        Organization org = organizationRepository.save(Organization.builder()
                .name(request.organizationName())
                .slug(slug)
                .subscriptionTier(SubscriptionTier.FREE)
                .build());

        Managers manager = managersRepository.save(Managers.builder()
                .firstName(request.adminFirstName())
                .lastName(request.adminLastName())
                .email(request.adminEmail())
                .organization(org)
                .build());

        Roles adminRole = rolesRepository.findByRole("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Ruolo ADMIN non trovato nel database"));

        Users admin = usersRepository.save(Users.builder()
                .username(request.adminUsername())
                .passwordHash(passwordEncoder.encode(request.adminPassword()))
                .role(adminRole)
                .manager(manager)
                .organization(org)
                .verified(true)
                .build());

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
