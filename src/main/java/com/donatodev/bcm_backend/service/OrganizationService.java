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
import com.donatodev.bcm_backend.dto.UpdateOrganizationRequest;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UsersRepository usersRepository;
    private final ManagersRepository managersRepository;
    private final RolesRepository rolesRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            UsersRepository usersRepository,
            ManagersRepository managersRepository,
            RolesRepository rolesRepository,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            RefreshTokenService refreshTokenService) {
        this.organizationRepository = organizationRepository;
        this.usersRepository = usersRepository;
        this.managersRepository = managersRepository;
        this.rolesRepository = rolesRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponseDTO registerOrganization(OrganizationRegistrationRequest request) {
        if (usersRepository.existsByUsername(request.adminUsername())) {
            throw new IllegalArgumentException("Username already exists.");
        }
        if (managersRepository.existsByEmail(request.adminEmail())) {
            throw new IllegalArgumentException("Email already in use.");
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
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found in database"));

        Users admin = usersRepository.save(Users.builder()
                .username(request.adminUsername())
                .passwordHash(passwordEncoder.encode(request.adminPassword()))
                .role(adminRole)
                .manager(manager)
                .organization(org)
                .verified(true)
                .build());

        String accessToken = jwtUtils.generateToken(admin);
        String refreshToken = refreshTokenService.createRefreshToken(admin).getToken();
        return new AuthResponseDTO(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public OrganizationDTO getMyOrganization() {
        Organization org = resolveCurrentOrganization();
        return toDTO(org);
    }

    @Transactional
    public OrganizationDTO updateMyOrganization(UpdateOrganizationRequest request) {
        Organization org = resolveCurrentOrganization();

        if (request.name() != null && !request.name().isBlank()) {
            org.setName(request.name());
        }
        if (request.subscriptionTier() != null) {
            org.setSubscriptionTier(request.subscriptionTier());
        }

        return toDTO(organizationRepository.save(org));
    }

    private Organization resolveCurrentOrganization() {
        Long orgId = TenantContext.get();
        if (orgId != null) {
            return organizationRepository.findById(orgId)
                    .orElseThrow(() -> new OrganizationNotFoundException("Organization not found"));
        }
        // Fallback when TenantContext is absent (e.g. test context with @WithMockUser)
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return usersRepository.findByUsername(username)
                .map(u -> {
                    if (u.getOrganization() == null) {
                        throw new OrganizationNotFoundException("User has no organization");
                    }
                    return u.getOrganization();
                })
                .orElseThrow(() -> new OrganizationNotFoundException("Authenticated user not found"));
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
                org.getCreatedAt());
    }
}
