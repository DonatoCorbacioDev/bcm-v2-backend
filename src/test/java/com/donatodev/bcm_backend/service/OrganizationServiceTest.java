package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.donatodev.bcm_backend.config.TenantContext;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.config.LegalTerms;
import com.donatodev.bcm_backend.dto.OrganizationDTO;
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
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.TermsAcceptanceRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class OrganizationServiceTest {

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

    @BeforeEach
    void enterTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void leaveTenant() {
        TenantContext.clear();
    }

    @Mock private OrganizationRepository organizationRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private ManagersRepository managersRepository;
    @Mock private RolesRepository rolesRepository;
    @Mock private TermsAcceptanceRepository termsAcceptanceRepository;
    @Mock private JwtUtils jwtUtils;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private com.donatodev.bcm_backend.repository.ContractsRepository contractsRepository;
    @Mock private TierLimitService tierLimitService;
    @Mock private AuditLogService auditLogService;
    @Mock private com.donatodev.bcm_backend.repository.FinancialTypesRepository financialTypesRepository;

    @InjectMocks
    private OrganizationService organizationService;

    private static final RequestOrigin CONFIRM_ORIGIN = new RequestOrigin("203.0.113.9", "Firefox");
    private static final LocalDateTime CODE_SENT_AT = LocalDateTime.of(2026, Month.OCTOBER, 28, 10, 0);

    private PendingRegistration pending(String orgName, String username, String email, String termsVersion) {
        return PendingRegistration.builder()
                .publicId("reg-1").organizationName(orgName)
                .adminFirstName("John").adminLastName("Doe")
                .adminEmail(email).adminUsername(username)
                .passwordHash("already-hashed").termsVersion(termsVersion)
                .codeHash("h").codeSentAt(CODE_SENT_AT).expiresAt(CODE_SENT_AT.plusMinutes(15))
                .requestIp("198.51.100.7").requestUserAgent("Chrome")
                .build();
    }

    private PendingRegistration validPending() {
        return pending("Acme Corp", "acme-admin", "admin@acme.com", LegalTerms.CURRENT_VERSION);
    }

    private Organization savedOrg() {
        return Organization.builder().id(uid(1)).name("Acme Corp").slug("acme-corp")
                .subscriptionTier(SubscriptionTier.FREE).build();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: OrganizationService")
    @SuppressWarnings("unused")
    class VerifyOrganizationService {

        @Test
        @Order(1)
        @DisplayName("createConfirmedOrganization creates org + admin and returns tokens")
        void shouldRegisterOrganizationAndReturnTokens() {
            Organization org = savedOrg();
            Managers manager = Managers.builder().id(uid(1)).email("admin@acme.com").build();
            Users admin = Users.builder().id(uid(1)).username("acme-admin").organization(org).build();

            when(usersRepository.existsByUsername("acme-admin")).thenReturn(false);
            when(managersRepository.existsByEmail("admin@acme.com")).thenReturn(false);
            when(organizationRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(organizationRepository.save(any())).thenReturn(org);
            when(managersRepository.save(any())).thenReturn(manager);
            when(rolesRepository.findByRole("ADMIN"))
                    .thenReturn(Optional.of(Roles.builder().role("ADMIN").build()));
            when(usersRepository.save(any())).thenReturn(admin);
            when(jwtUtils.generateToken(admin)).thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(admin)).thenReturn("refresh-token");

            var response = organizationService.createConfirmedOrganization(validPending(), CONFIRM_ORIGIN);

            assertEquals("access-token", response.token());
            assertEquals("refresh-token", response.refreshToken());

            ArgumentCaptor<Organization> created = ArgumentCaptor.forClass(Organization.class);
            verify(organizationRepository).save(created.capture());
            assertEquals(LegalTerms.CURRENT_VERSION, created.getValue().getTermsVersion());
            assertNotNull(created.getValue().getTermsAcceptedAt());
            verify(auditLogService).save(eq("TERMS_ACCEPTED"), eq("Organization"), eq(uid(1)), eq("acme-admin"),
                    eq(uid(1)), contains(LegalTerms.CURRENT_VERSION));

            ArgumentCaptor<Users> user = ArgumentCaptor.forClass(Users.class);
            verify(usersRepository).save(user.capture());
            assertEquals("already-hashed", user.getValue().getPasswordHash());
            assertTrue(user.getValue().isVerified());

            ArgumentCaptor<TermsAcceptance> evidence = ArgumentCaptor.forClass(TermsAcceptance.class);
            verify(termsAcceptanceRepository).save(evidence.capture());
            TermsAcceptance signed = evidence.getValue();
            assertEquals(uid(1), signed.getOrganizationId());
            assertEquals("Acme Corp", signed.getOrganizationName());
            assertEquals(uid(1), signed.getUserId());
            assertEquals("acme-admin", signed.getUsername());
            assertEquals("admin@acme.com", signed.getSignerEmail());
            assertEquals(LegalTerms.CURRENT_VERSION, signed.getTermsVersion());
            assertEquals(LegalTerms.ONEROUS_CLAUSES, signed.getOnerousClauses());
            assertTrue(signed.isProfessionalUseDeclared());
            assertEquals(TermsAcceptance.SIGNATURE_EMAIL_OTP, signed.getSignatureMethod());
            assertEquals(CODE_SENT_AT, signed.getCodeSentAt());
            assertEquals(created.getValue().getTermsAcceptedAt(), signed.getAcceptedAt());
            assertEquals("198.51.100.7", signed.getRequestIp());
            assertEquals("Chrome", signed.getRequestUserAgent());
            assertEquals("203.0.113.9", signed.getConfirmIp());
            assertEquals("Firefox", signed.getConfirmUserAgent());
        }

        @Test
        @Order(2)
        @DisplayName("createConfirmedOrganization fails loudly when the ADMIN role is missing from the database")
        void shouldFailWithoutAdminRole() {
            Organization org = savedOrg();
            when(organizationRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(organizationRepository.save(any())).thenReturn(org);
            when(managersRepository.save(any())).thenReturn(Managers.builder().id(uid(1)).email("admin@acme.com").build());
            when(rolesRepository.findByRole("ADMIN")).thenReturn(Optional.empty());

            PendingRegistration pending = validPending();
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> organizationService.createConfirmedOrganization(pending, CONFIRM_ORIGIN));
            assertEquals("Ruolo ADMIN non trovato nel database", e.getMessage());
            verify(usersRepository, never()).save(any());
        }

        @Test
        @Order(2)
        @DisplayName("createConfirmedOrganization gives the new organization Costi and Ricavi")
        void shouldCreateDefaultFinancialTypes() {
            Organization org = savedOrg();
            Users admin = Users.builder().id(uid(1)).username("acme-admin").organization(org).build();
            when(organizationRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(organizationRepository.save(any())).thenReturn(org);
            when(managersRepository.save(any())).thenReturn(Managers.builder().id(uid(1)).email("admin@acme.com").build());
            when(rolesRepository.findByRole("ADMIN")).thenReturn(Optional.of(Roles.builder().role("ADMIN").build()));
            when(usersRepository.save(any())).thenReturn(admin);

            organizationService.createConfirmedOrganization(validPending(), CONFIRM_ORIGIN);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<com.donatodev.bcm_backend.entity.FinancialTypes>> types =
                    ArgumentCaptor.forClass(List.class);
            verify(financialTypesRepository).saveAll(types.capture());
            assertEquals(List.of("Costi:COST", "Ricavi:REVENUE"), types.getValue().stream()
                    .map(t -> t.getName() + ":" + t.getCategory()).toList(),
                    "a new organization can enter a contract's annual value right away");
            assertTrue(types.getValue().stream().allMatch(t -> t.getOrganization() == org));
        }

        @Test
        @DisplayName("createConfirmedOrganization refuses a stale Terms version before creating anything")
        void shouldRefuseStaleTermsVersion() {
            PendingRegistration stale = pending("Acme Corp", "acme-admin", "admin@acme.com", "2020-01-01");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> organizationService.createConfirmedOrganization(stale, CONFIRM_ORIGIN));
            assertTrue(ex.getMessage().contains("Termini di servizio sono stati aggiornati"));
            verify(organizationRepository, never()).save(any());
        }

        @Test
        @Order(2)
        @DisplayName("createConfirmedOrganization throws when username already exists")
        void shouldThrowWhenUsernameExists() {
            when(usersRepository.existsByUsername("acme-admin")).thenReturn(true);
            var request = validPending();
            assertThrows(IllegalArgumentException.class,
                    () -> organizationService.createConfirmedOrganization(request, CONFIRM_ORIGIN));
        }

        @Test
        @Order(3)
        @DisplayName("createConfirmedOrganization throws when email already in use")
        void shouldThrowWhenEmailExists() {
            when(usersRepository.existsByUsername("acme-admin")).thenReturn(false);
            when(managersRepository.existsByEmail("admin@acme.com")).thenReturn(true);
            var request = validPending();
            assertThrows(IllegalArgumentException.class,
                    () -> organizationService.createConfirmedOrganization(request, CONFIRM_ORIGIN));
        }

        @Test
        @Order(4)
        @DisplayName("createConfirmedOrganization appends suffix when slug already exists")
        void shouldGenerateUniqueSlugWhenBaseSlugTaken() {
            Organization org = savedOrg();
            Managers manager = Managers.builder().id(uid(1)).build();
            Users admin = Users.builder().id(uid(1)).username("acme-admin").organization(org).build();

            when(usersRepository.existsByUsername(anyString())).thenReturn(false);
            when(managersRepository.existsByEmail(anyString())).thenReturn(false);
            when(organizationRepository.findBySlug(anyString())).thenAnswer(inv -> {
                String s = inv.getArgument(0);
                return s.equals("acme-corp") ? Optional.of(org) : Optional.empty();
            });
            when(organizationRepository.save(any())).thenReturn(org);
            when(managersRepository.save(any())).thenReturn(manager);
            when(rolesRepository.findByRole("ADMIN"))
                    .thenReturn(Optional.of(Roles.builder().role("ADMIN").build()));
            when(usersRepository.save(any())).thenReturn(admin);
            when(jwtUtils.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any())).thenReturn("rt");

            var response = organizationService.createConfirmedOrganization(validPending(), CONFIRM_ORIGIN);
            assertNotNull(response.token());
        }

        @Test
        @Order(5)
        @DisplayName("updateMyOrganization updates name via TenantContext; subscriptionTier is not self-service")
        void shouldUpdateOrganization() {
            Organization org = Organization.builder()
                    .id(uid(1)).name("Old Name").slug("old-name")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            com.donatodev.bcm_backend.config.TenantContext.set(uid(1));
            try {
                when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));
                when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                UpdateOrganizationRequest req = new UpdateOrganizationRequest("New Name", null, null);
                OrganizationDTO result = organizationService.updateMyOrganization(req);

                assertEquals("New Name", result.name());
                assertEquals(SubscriptionTier.FREE, result.subscriptionTier());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(6)
        @DisplayName("getMyOrganization throws OrganizationNotFoundException when org not found")
        void shouldThrowWhenOrgNotFound() {
            com.donatodev.bcm_backend.config.TenantContext.set(uid(99));
            try {
                when(organizationRepository.findById(uid(99))).thenReturn(Optional.empty());
                assertThrows(OrganizationNotFoundException.class,
                        () -> organizationService.getMyOrganization());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(7)
        @DisplayName("createConfirmedOrganization with special-char-only name uses 'org' as slug base")
        void shouldUseOrgSlugWhenNameProducesEmptyBase() {
            Organization org = savedOrg();
            Managers manager = Managers.builder().id(uid(1)).build();
            Users admin = Users.builder().id(uid(1)).username("acme-admin").organization(org).build();

            PendingRegistration req = pending("!!!###", "spec-admin", "spec@test.com", LegalTerms.CURRENT_VERSION);

            when(usersRepository.existsByUsername("spec-admin")).thenReturn(false);
            when(managersRepository.existsByEmail("spec@test.com")).thenReturn(false);
            when(organizationRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(organizationRepository.save(any())).thenReturn(org);
            when(managersRepository.save(any())).thenReturn(manager);
            when(rolesRepository.findByRole("ADMIN"))
                    .thenReturn(Optional.of(com.donatodev.bcm_backend.entity.Roles.builder().role("ADMIN").build()));
            when(usersRepository.save(any())).thenReturn(admin);
            when(jwtUtils.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any())).thenReturn("rt");

            var response = organizationService.createConfirmedOrganization(req, CONFIRM_ORIGIN);

            assertNotNull(response.token());
        }

        @Test
        @Order(8)
        @DisplayName("updateMyOrganization with null name does not update name")
        void shouldNotUpdateNameWhenNull() {
            Organization org = Organization.builder().id(uid(1)).name("Original").slug("original")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            com.donatodev.bcm_backend.config.TenantContext.set(uid(1));
            try {
                when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));
                when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                // name = null → should NOT update name
                UpdateOrganizationRequest req = new UpdateOrganizationRequest(null, null, null);
                OrganizationDTO result = organizationService.updateMyOrganization(req);

                assertEquals("Original", result.name()); // name unchanged
                assertEquals(SubscriptionTier.FREE, result.subscriptionTier()); // tier unchanged
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(9)
        @DisplayName("updateMyOrganization with blank name does not update name")
        void shouldNotUpdateNameWhenBlank() {
            Organization org = Organization.builder().id(uid(1)).name("Original").slug("original")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            com.donatodev.bcm_backend.config.TenantContext.set(uid(1));
            try {
                when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));
                when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                // name = "  " (blank) → should NOT update name
                UpdateOrganizationRequest req = new UpdateOrganizationRequest("   ", null, null);
                OrganizationDTO result = organizationService.updateMyOrganization(req);

                assertEquals("Original", result.name()); // name unchanged
                assertEquals(SubscriptionTier.FREE, result.subscriptionTier()); // tier unchanged
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(11)
        @DisplayName("getMyOrganization refuses a request without an organization, whoever is logged in")
        void shouldGetMyOrganizationViaSecurityContextFallback() {
            TenantContext.clear();
            assertThrows(com.donatodev.bcm_backend.exception.MissingTenantException.class, () -> organizationService.getMyOrganization());
        }

        @Test
        @Order(14)
        @DisplayName("updateMyOrganization accepts a valid IBAN, normalizing case and spaces")
        void shouldUpdateValidIban() {
            Organization org = Organization.builder().id(uid(1)).name("Acme").slug("acme")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            com.donatodev.bcm_backend.config.TenantContext.set(uid(1));
            try {
                when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));
                when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                UpdateOrganizationRequest req = new UpdateOrganizationRequest(
                        null, "de89 3704 0044 0532 0130 00", "cobadeffxxx");
                OrganizationDTO result = organizationService.updateMyOrganization(req);

                // The response is masked, but the stored entity keeps the real value.
                assertEquals("DE...3000", result.iban());
                assertEquals("CO...FXXX", result.bic());
                assertEquals("DE89370400440532013000", org.getIban());
                assertEquals("COBADEFFXXX", org.getBic());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(15)
        @DisplayName("updateMyOrganization rejects an invalid IBAN")
        void shouldRejectInvalidIban() {
            Organization org = Organization.builder().id(uid(1)).name("Acme").slug("acme")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            com.donatodev.bcm_backend.config.TenantContext.set(uid(1));
            try {
                when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));

                UpdateOrganizationRequest req = new UpdateOrganizationRequest(
                        null, "IT00X0000000000000000000000", null);

                assertThrows(IllegalArgumentException.class,
                        () -> organizationService.updateMyOrganization(req));
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(16)
        @DisplayName("updateMyOrganization with blank IBAN/BIC clears existing values")
        void shouldClearIbanAndBicWhenBlank() {
            Organization org = Organization.builder().id(uid(1)).name("Acme").slug("acme")
                    .subscriptionTier(SubscriptionTier.FREE).iban("DE89370400440532013000").bic("COBADEFFXXX").build();

            com.donatodev.bcm_backend.config.TenantContext.set(uid(1));
            try {
                when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));
                when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                UpdateOrganizationRequest req = new UpdateOrganizationRequest(null, "", "");
                OrganizationDTO result = organizationService.updateMyOrganization(req);

                assertNull(result.iban());
                assertNull(result.bic());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(17)
        @DisplayName("getMyOrganization returns the IBAN/BIC masked, not the full value")
        void shouldReturnMaskedIbanOnGet() {
            Organization org = Organization.builder().id(uid(1)).name("Acme").slug("acme")
                    .subscriptionTier(SubscriptionTier.FREE)
                    .iban("DE89370400440532013000").bic("COBADEFFXXX").build();

            com.donatodev.bcm_backend.config.TenantContext.set(uid(1));
            try {
                when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));

                OrganizationDTO result = organizationService.getMyOrganization();

                assertEquals("DE...3000", result.iban());
                assertEquals("CO...FXXX", result.bic());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(18)
        @DisplayName("getUsage returns contract count and the tier's limit")
        void shouldReturnUsageForLimitedTier() {
            Organization org = Organization.builder().id(uid(1)).name("Acme").slug("acme")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            com.donatodev.bcm_backend.config.TenantContext.set(uid(1));
            try {
                when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));
                when(contractsRepository.countAllContractsByOrg(uid(1))).thenReturn(8);
                when(tierLimitService.getContractLimit(SubscriptionTier.FREE)).thenReturn(15);

                var result = organizationService.getUsage();

                assertEquals(8, result.contractsUsed());
                assertEquals(15, result.contractsLimit());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(19)
        @DisplayName("getUsage returns a null limit for an unlimited tier")
        void shouldReturnNullLimitForUnlimitedTier() {
            Organization org = Organization.builder().id(uid(1)).name("Acme").slug("acme")
                    .subscriptionTier(SubscriptionTier.ENTERPRISE).build();

            com.donatodev.bcm_backend.config.TenantContext.set(uid(1));
            try {
                when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));
                when(contractsRepository.countAllContractsByOrg(uid(1))).thenReturn(300);
                when(tierLimitService.getContractLimit(SubscriptionTier.ENTERPRISE)).thenReturn(null);

                var result = organizationService.getUsage();

                assertEquals(300, result.contractsUsed());
                assertNull(result.contractsLimit());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }
    }

    @Nested
    @DisplayName("linkStripeCustomer")
    @SuppressWarnings("unused")
    class LinkStripeCustomer {

        @Test
        @DisplayName("stores the Stripe customer, and the DTO then reports the portal as available")
        void shouldStoreCustomer() {
            Organization org = savedOrg();
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));

            organizationService.linkStripeCustomer(uid(1), "cus_1");

            assertEquals("cus_1", org.getStripeCustomerId());
            verify(organizationRepository).save(org);
            when(organizationRepository.save(org)).thenReturn(org);
            assertTrue(organizationService.changeTier(uid(1), SubscriptionTier.PRO, "stripe").billingPortalAvailable());
        }

        @Test
        @DisplayName("writes nothing when the same customer is already stored (webhook redelivery)")
        void shouldSkipWhenUnchanged() {
            Organization org = savedOrg();
            org.setStripeCustomerId("cus_1");
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));

            organizationService.linkStripeCustomer(uid(1), "cus_1");

            verify(organizationRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when the organization does not exist")
        void shouldThrowWhenOrganizationMissing() {
            when(organizationRepository.findById(uid(99))).thenReturn(Optional.empty());

            UUID id99 = uid(99);
            assertThrows(OrganizationNotFoundException.class,
                    () -> organizationService.linkStripeCustomer(id99, "cus_1"));
        }
    }

    @Nested
    @DisplayName("changeTier")
    @SuppressWarnings("unused")
    class ChangeTier {

        @Test
        @DisplayName("updates the tier and records who triggered it in the audit log")
        void shouldChangeTierAndAudit() {
            Organization org = savedOrg();
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));
            when(organizationRepository.save(org)).thenReturn(org);

            OrganizationDTO result = organizationService.changeTier(uid(1), SubscriptionTier.PRO, "manual");

            assertEquals(SubscriptionTier.PRO, result.subscriptionTier());
            verify(auditLogService).save("TIER_CHANGED", "Organization", uid(1), "system:manual", uid(1), "FREE -> PRO");
        }

        @Test
        @DisplayName("is a no-op without an audit entry when the tier is unchanged")
        void shouldNotAuditWhenTierUnchanged() {
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(savedOrg()));

            OrganizationDTO result = organizationService.changeTier(uid(1), SubscriptionTier.FREE, "manual");

            assertEquals(SubscriptionTier.FREE, result.subscriptionTier());
            verify(organizationRepository, never()).save(any());
            verify(auditLogService, never()).save(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("throws when the organization does not exist")
        void shouldThrowWhenOrganizationMissing() {
            when(organizationRepository.findById(uid(99))).thenReturn(Optional.empty());

            UUID id99 = uid(99);
            assertThrows(OrganizationNotFoundException.class,
                    () -> organizationService.changeTier(id99, SubscriptionTier.PRO, "manual"));
        }
    }
}
