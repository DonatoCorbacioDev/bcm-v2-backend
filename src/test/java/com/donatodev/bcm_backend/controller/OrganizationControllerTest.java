package com.donatodev.bcm_backend.controller;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import com.donatodev.bcm_backend.support.TestTenant;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import com.donatodev.bcm_backend.entity.PendingRegistration;
import com.donatodev.bcm_backend.entity.TermsAcceptance;
import com.donatodev.bcm_backend.repository.PendingRegistrationRepository;
import com.donatodev.bcm_backend.repository.TermsAcceptanceRepository;
import com.donatodev.bcm_backend.service.DummyEmailService;
import com.donatodev.bcm_backend.service.OrganizationRegistrationService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.auth.RefreshCookieFactory;
import com.donatodev.bcm_backend.config.LegalTerms;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.AuditLogRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrganizationControllerTest {

    /** The organization this class's requests and fixtures belong to, as a real token's would. */
    private Organization testOrg;

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private ManagersRepository managersRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private com.donatodev.bcm_backend.repository.TotpRecoveryCodeRepository totpRecoveryCodeRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PendingRegistrationRepository pendingRegistrationRepository;
    @Autowired private TermsAcceptanceRepository termsAcceptanceRepository;
    @Autowired private com.donatodev.bcm_backend.repository.FinancialTypesRepository financialTypesRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationRegistrationService registrationService;
    @MockitoSpyBean private DummyEmailService dummyEmailService;

    private static final String REGISTER_URL = "/organizations/register";
    private static final String CONFIRM_URL = "/organizations/register/confirm";
    private static final String RESEND_URL = "/organizations/register/resend";
    private static final String ME_URL = "/organizations/me";


    @AfterEach
    void leaveTestTenant() {
        TestTenant.clear();
    }

    @BeforeEach
    void setup() {
        clearInvocations(dummyEmailService);
        pendingRegistrationRepository.deleteAll();
        termsAcceptanceRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        totpRecoveryCodeRepository.deleteAll(); // the 2FA setup after registration creates them
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();
        financialTypesRepository.deleteAll(); // registration creates Costi and Ricavi
        organizationRepository.deleteAll();
        rolesRepository.save(Roles.builder().role("ADMIN").build());
        rolesRepository.save(Roles.builder().role("MANAGER").build());
        testOrg = organizationRepository.save(Organization.builder()
                .name("Test org").slug("test-org-" + UUID.randomUUID()).build());
        TestTenant.use(testOrg.getId());
    }

    private static String registration(String orgName, String username, String email, String termsVersion,
            boolean terms, boolean professional, boolean clauses) {
        return """
                {
                  "organizationName": "%s",
                  "adminUsername":    "%s",
                  "adminPassword":    "password123",
                  "adminEmail":       "%s",
                  "adminFirstName":   "Test",
                  "adminLastName":    "Admin",
                  "termsVersion":     "%s",
                  "termsAccepted":    %s,
                  "professionalUse":  %s,
                  "onerousClausesApproved": %s
                }
                """.formatted(orgName, username, email, termsVersion, terms, professional, clauses);
    }

    private static String registration(String orgName, String username, String email) {
        return registration(orgName, username, email, LegalTerms.CURRENT_VERSION, true, true, true);
    }

    /** Step 1: returns the registrationId. */
    private String start(String body) throws Exception {
        String response = mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.registrationId");
    }

    /** The last code the (spied) dev email service was asked to send to this address. */
    private String emailedCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(dummyEmailService, atLeastOnce()).sendRegistrationCode(eq(email), code.capture(), anyInt());
        return code.getValue();
    }

    private static String currentTotp(String secret) throws Exception {
        java.lang.reflect.Method m = com.donatodev.bcm_backend.util.TotpUtil.class
                .getDeclaredMethod("generateCode", String.class, long.class);
        m.setAccessible(true);
        return (String) m.invoke(null, secret, java.time.Instant.now().getEpochSecond() / 30);
    }

    private ResultActions confirm(String registrationId, String code) throws Exception {
        return mockMvc.perform(post(CONFIRM_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"registrationId\":\"%s\",\"code\":\"%s\"}".formatted(registrationId, code)));
    }

    private void registerFully(String orgName, String username, String email) throws Exception {
        String id = start(registration(orgName, username, email));
        confirm(id, emailedCode(email)).andExpect(status().isCreated());
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("POST /organizations/register (step 1: code emailed)")
    @SuppressWarnings("unused")
    class Register {

        @Test
        @Order(1)
        @DisplayName("returns 202 with a registration id, emails a code, and creates nothing yet")
        void shouldStartWithoutCreatingAnything() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("User-Agent", "U".repeat(300))
                    .content(registration("Test Corp", "testadmin", "admin@testcorp.com")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.registrationId").exists())
                    .andExpect(jsonPath("$.expiresInMinutes").value(15))
                    .andExpect(jsonPath("$.resendCooldownSeconds").value(60))
                    .andExpect(jsonPath("$.token").doesNotExist());

            assertThat(emailedCode("admin@testcorp.com")).matches("\\d{6}");
            assertTrue(usersRepository.findByUsername("testadmin").isEmpty());
            assertTrue(organizationRepository.findAll().stream().noneMatch(o -> "Test Corp".equals(o.getName())));

            PendingRegistration pending = pendingRegistrationRepository.findAll().get(0);
            assertNotEquals("password123", pending.getPasswordHash());
            assertTrue(passwordEncoder.matches("password123", pending.getPasswordHash()));
            assertEquals(255, pending.getRequestUserAgent().length());
            assertEquals("127.0.0.1", pending.getRequestIp());
        }

        @Test
        @Order(2)
        @DisplayName("duplicate username returns 400")
        void shouldRejectDuplicateUsername() throws Exception {
            registerFully("Test Corp", "testadmin", "admin@testcorp.com");

            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration("Another Corp", "testadmin", "other@corp.com")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Username già esistente")));
        }

        @Test
        @Order(3)
        @DisplayName("duplicate email returns 400")
        void shouldRejectDuplicateEmail() throws Exception {
            registerFully("Test Corp", "testadmin", "admin@testcorp.com");

            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration("Another Corp", "otheradmin", "admin@testcorp.com")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Email già in uso")));
        }

        @ParameterizedTest(name = "org=''{0}'', email=''{1}''")
        @CsvSource({"'', admin@corp.com", "Corp, not-an-email"})
        @DisplayName("blank or malformed fields return 400")
        void shouldRejectInvalidFields(String orgName, String email) throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration(orgName == null ? "" : orgName, "admin", email)))
                    .andExpect(status().isBadRequest());

            assertEquals(0, pendingRegistrationRepository.count());
        }

        @Test
        @DisplayName("requesting again with the same email replaces the earlier attempt and keeps its count of codes")
        void shouldReplaceEarlierAttemptForSameEmail() throws Exception {
            String first = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));
            String firstCode = emailedCode("admin@testcorp.com");
            codeSentAgo(first, Duration.ofSeconds(61), 1);
            String second = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));

            confirm(first, firstCode)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("scaduta")));
            assertEquals(1, pendingRegistrationRepository.count());
            assertEquals(2, pendingRegistrationRepository.findByPublicId(second).orElseThrow().getCodesSent());
        }

        @Test
        @DisplayName("OWASP API6: starting over within the cooldown sends no new code")
        void shouldRefuseRestartDuringCooldown() throws Exception {
            String first = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));
            clearInvocations(dummyEmailService);

            mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON)
                            .content(registration("Test Corp", "testadmin", "admin@testcorp.com")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Attendi")));
            verify(dummyEmailService, never()).sendRegistrationCode(anyString(), anyString(), anyInt());
            // The earlier attempt is untouched: its code still works.
            assertTrue(pendingRegistrationRepository.findByPublicId(first).isPresent());
        }

        @Test
        @DisplayName("OWASP API6: starting over is no way past five codes a day to one address")
        void shouldRefuseRestartAfterFiveCodesInADay() throws Exception {
            String id = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));
            codeSentAgo(id, Duration.ofMinutes(30), 5);
            clearInvocations(dummyEmailService);

            mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON)
                            .content(registration("Test Corp", "testadmin", "admin@testcorp.com")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("troppi codici")));
            verify(dummyEmailService, never()).sendRegistrationCode(anyString(), anyString(), anyInt());

            // A day later the address may start again, with a fresh count.
            codeSentAgo(id, Duration.ofHours(25), 5);
            String again = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));
            assertEquals(1, pendingRegistrationRepository.findByPublicId(again).orElseThrow().getCodesSent());
        }
    }

    /** Pretends the last code of a registration went out some time ago, after a number of codes. */
    private void codeSentAgo(String registrationId, Duration ago, int codesSent) {
        PendingRegistration pending = pendingRegistrationRepository.findByPublicId(registrationId).orElseThrow();
        pending.setCodeSentAt(LocalDateTime.now().minus(ago));
        pending.setCodesSent(codesSent);
        pendingRegistrationRepository.save(pending);
    }

    private void expiredAgo(String registrationId, Duration ago) {
        PendingRegistration pending = pendingRegistrationRepository.findByPublicId(registrationId).orElseThrow();
        pending.setExpiresAt(LocalDateTime.now().minus(ago));
        pendingRegistrationRepository.save(pending);
    }

    @Nested
    @DisplayName("POST /organizations/register/confirm (step 2: code typed back)")
    @SuppressWarnings("unused")
    class Confirm {

        @Test
        @DisplayName("the right code creates the organization: 201 with access token and refresh cookie")
        void shouldCreateOrganizationWithRightCode() throws Exception {
            String id = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));

            // The new admin gets no session yet: only a token to set 2FA up.
            String body = confirm(id, emailedCode("admin@testcorp.com"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andExpect(jsonPath("$.mfaSetupRequired").value(true))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andExpect(cookie().doesNotExist(RefreshCookieFactory.COOKIE_NAME))
                    .andReturn().getResponse().getContentAsString();
            String setupToken = com.jayway.jsonpath.JsonPath.read(body, "$.setupToken");

            Users admin = usersRepository.findByUsername("testadmin").orElseThrow();
            assertTrue(admin.isVerified());
            assertTrue(passwordEncoder.matches("password123", admin.getPasswordHash()));
            assertEquals(0, pendingRegistrationRepository.count());

            // The setup token opens nothing else.
            mockMvc.perform(get("/organizations/me").header("Authorization", "Bearer " + setupToken))
                    .andExpect(status().isUnauthorized());

            String secret = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/auth/2fa/enroll/setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"setupToken\":\"%s\"}".formatted(setupToken)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), "$.secret");

            String session = mockMvc.perform(post("/auth/2fa/enroll/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"setupToken\":\"%s\",\"code\":\"%s\"}".formatted(setupToken, currentTotp(secret))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                    .andExpect(cookie().exists(RefreshCookieFactory.COOKIE_NAME))
                    .andReturn().getResponse().getContentAsString();
            assertTrue(usersRepository.findByUsername("testadmin").orElseThrow().isTotpEnabled());

            mockMvc.perform(get("/organizations/me")
                            .header("Authorization", "Bearer " + com.jayway.jsonpath.JsonPath.read(session, "$.token")))
                    .andExpect(status().isOk());

            // Once 2FA is on, the same setup token can't replace the secret.
            mockMvc.perform(post("/auth/2fa/enroll/setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"setupToken\":\"%s\"}".formatted(setupToken)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a wrong code returns 400 and creates nothing; the right one still works afterwards")
        void shouldRefuseWrongCode() throws Exception {
            String id = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));
            String code = emailedCode("admin@testcorp.com");

            confirm(id, wrong(code))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Codice non corretto."));
            assertTrue(usersRepository.findByUsername("testadmin").isEmpty());
            assertEquals(1, pendingRegistrationRepository.findAll().get(0).getFailedAttempts());

            confirm(id, code).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("five wrong codes close the registration, so the right code no longer works")
        void shouldDropRegistrationAfterTooManyWrongCodes() throws Exception {
            String id = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));
            String code = emailedCode("admin@testcorp.com");

            for (int i = 1; i < 5; i++) {
                confirm(id, wrong(code)).andExpect(jsonPath("$.message").value("Codice non corretto."));
            }
            confirm(id, wrong(code))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Troppi tentativi")));

            confirm(id, code).andExpect(status().isBadRequest());
            // Closed, not deleted: the row still counts the codes sent to the address.
            assertEquals(1, pendingRegistrationRepository.count());
            assertTrue(pendingRegistrationRepository.findByPublicId(id).orElseThrow().getExpiresAt()
                    .isBefore(LocalDateTime.now().plusSeconds(1)));
        }

        @Test
        @DisplayName("an expired code returns 400; the attempt stays until the purge")
        void shouldRefuseExpiredCode() throws Exception {
            String id = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));
            String code = emailedCode("admin@testcorp.com");
            PendingRegistration pending = pendingRegistrationRepository.findByPublicId(id).orElseThrow();
            pending.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            pendingRegistrationRepository.save(pending);

            confirm(id, code)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("scaduta")));
            assertEquals(1, pendingRegistrationRepository.count());
        }

        @ParameterizedTest(name = "id={0}, code={1}")
        @CsvSource({"unknown-id, 123456", "'', 123456", "some-id, 12ab56", "some-id, 1234567"})
        @DisplayName("an unknown registration or a malformed code returns 400")
        void shouldRefuseUnknownOrMalformed(String id, String code) throws Exception {
            confirm(id == null ? "" : id, code).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a username taken between request and confirmation is refused at confirmation")
        void shouldRecheckUniquenessAtConfirmation() throws Exception {
            String first = start(registration("First Corp", "sameadmin", "first@corp.com"));
            String firstCode = emailedCode("first@corp.com");
            registerFully("Second Corp", "sameadmin", "second@corp.com");

            confirm(first, firstCode)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Username già esistente")));
        }

        @Test
        @DisplayName("the hourly purge removes registrations expired for over a day, and only those")
        void shouldPurgeOnlyExpired() throws Exception {
            String old = start(registration("Old Corp", "oldadmin", "old@corp.com"));
            String recent = start(registration("Recent Corp", "recentadmin", "recent@corp.com"));
            start(registration("New Corp", "newadmin", "new@corp.com"));
            expiredAgo(old, Duration.ofHours(25));
            expiredAgo(recent, Duration.ofMinutes(1));

            registrationService.purgeExpired();

            assertEquals(2, pendingRegistrationRepository.count());
            assertTrue(pendingRegistrationRepository.findByPublicId(old).isEmpty());
            // Kept for the day: it still limits the codes recent@corp.com can receive.
            assertTrue(pendingRegistrationRepository.findByPublicId(recent).isPresent());
        }
    }

    @Nested
    @DisplayName("POST /organizations/register/resend")
    @SuppressWarnings("unused")
    class Resend {

        private ResultActions resend(String registrationId) throws Exception {
            return mockMvc.perform(post(RESEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"registrationId\":\"%s\"}".formatted(registrationId)));
        }

        private void sentAMinuteAgo(String registrationId, int codesSent) {
            PendingRegistration pending = pendingRegistrationRepository.findByPublicId(registrationId).orElseThrow();
            pending.setCodeSentAt(LocalDateTime.now().minusSeconds(61));
            pending.setCodesSent(codesSent);
            pendingRegistrationRepository.save(pending);
        }

        @Test
        @DisplayName("refuses a new code during the cooldown")
        void shouldRefuseDuringCooldown() throws Exception {
            String id = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));

            resend(id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Attendi")));
        }

        @Test
        @DisplayName("after the cooldown sends a new code that replaces the old one")
        void shouldSendNewCodeAfterCooldown() throws Exception {
            String id = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));
            String oldCode = emailedCode("admin@testcorp.com");
            confirm(id, wrong(oldCode)).andExpect(status().isBadRequest());
            sentAMinuteAgo(id, 1);

            resend(id)
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.registrationId").value(id));
            String newCode = emailedCode("admin@testcorp.com");
            PendingRegistration pending = pendingRegistrationRepository.findByPublicId(id).orElseThrow();
            assertEquals(2, pending.getCodesSent());
            assertEquals(0, pending.getFailedAttempts());

            if (!newCode.equals(oldCode)) {
                confirm(id, oldCode).andExpect(jsonPath("$.message").value("Codice non corretto."));
            }
            confirm(id, newCode).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("refuses more than five codes for one registration")
        void shouldRefuseTooManyCodes() throws Exception {
            String id = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));
            sentAMinuteAgo(id, 5);

            resend(id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("troppi codici")));
        }

        @Test
        @DisplayName("refuses an unknown or expired registration")
        void shouldRefuseUnknown() throws Exception {
            resend("does-not-exist")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("scaduta")));
        }

        @Test
        @DisplayName("refuses an expired registration, which stays until the purge")
        void shouldRefuseExpired() throws Exception {
            String id = start(registration("Test Corp", "testadmin", "admin@testcorp.com"));
            PendingRegistration pending = pendingRegistrationRepository.findByPublicId(id).orElseThrow();
            pending.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            pendingRegistrationRepository.save(pending);

            resend(id).andExpect(status().isBadRequest());
            assertEquals(1, pendingRegistrationRepository.count());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("GET /organizations/me")
    @SuppressWarnings("unused")
    class GetMe {

        private void seedAdminUser() {
            Organization org = organizationRepository.save(
                    Organization.builder().name("My Corp").slug("my-corp")
                            .subscriptionTier(SubscriptionTier.FREE).build());
            TestTenant.use(org.getId());
            Managers manager = managersRepository.save(
                    Managers.builder().organization(org).firstName("Admin").lastName("User")
                            .email("admin@mycorp.com").organization(org).build());
            Roles role = rolesRepository.findByRole("ADMIN").orElseThrow();
            usersRepository.save(Users.builder().organization(org)
                    .username("org-admin").passwordHash("pwd").verified(true)
                    .role(role).manager(manager).organization(org).build());
        }

        @Test
        @Order(1)
        @WithMockUser(username = "org-admin", roles = "ADMIN")
        @DisplayName("Admin retrieves their organization profile")
        void shouldReturnOrgProfile() throws Exception {
            seedAdminUser();
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("My Corp"))
                    .andExpect(jsonPath("$.slug").value("my-corp"))
                    .andExpect(jsonPath("$.subscriptionTier").value("FREE"));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager is denied access")
        void shouldReturn403ForManager() throws Exception {
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(3)
        @DisplayName("Unauthenticated request returns 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Order(4)
        @WithMockUser(username = "no-org-user", roles = "ADMIN")
        @DisplayName("A request without an organization is refused (403)")
        void shouldRefuseRequestWithoutOrganization() throws Exception {
            Roles role = rolesRepository.findByRole("ADMIN").orElseThrow();
            usersRepository.save(Users.builder().organization(testOrg)
                    .username("no-org-user").passwordHash("pwd").verified(true)
                    .role(role).build());
            // No organization on the request: refused, not resolved some other way.
            TestTenant.clear();
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("GET /organizations/me/usage")
    @SuppressWarnings("unused")
    class GetMyOrganizationUsage {

        private void seedAdminUser(SubscriptionTier tier) {
            Organization org = organizationRepository.save(
                    Organization.builder().name("Usage Corp").slug("usage-corp")
                            .subscriptionTier(tier).build());
            TestTenant.use(org.getId());
            Managers manager = managersRepository.save(
                    Managers.builder().organization(org).firstName("Admin").lastName("User")
                            .email("admin@usagecorp.com").organization(org).build());
            Roles role = rolesRepository.findByRole("ADMIN").orElseThrow();
            usersRepository.save(Users.builder().organization(org)
                    .username("usage-admin").passwordHash("pwd").verified(true)
                    .role(role).manager(manager).organization(org).build());
        }

        @Test
        @Order(1)
        @WithMockUser(username = "usage-admin", roles = "ADMIN")
        @DisplayName("FREE tier: returns 0 contracts used against a limit of 15")
        void shouldReturnUsageForFreeTier() throws Exception {
            seedAdminUser(SubscriptionTier.FREE);
            mockMvc.perform(get(ME_URL + "/usage"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contractsUsed").value(0))
                    .andExpect(jsonPath("$.contractsLimit").value(15));
        }

        @Test
        @Order(2)
        @WithMockUser(username = "usage-admin", roles = "ADMIN")
        @DisplayName("ENTERPRISE tier: contractsLimit is null (unlimited)")
        void shouldReturnNullLimitForEnterpriseTier() throws Exception {
            seedAdminUser(SubscriptionTier.ENTERPRISE);
            mockMvc.perform(get(ME_URL + "/usage"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contractsUsed").value(0))
                    .andExpect(jsonPath("$.contractsLimit").doesNotExist());
        }

        @Test
        @Order(3)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager is denied access")
        void shouldReturn403ForManager() throws Exception {
            mockMvc.perform(get(ME_URL + "/usage"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("PUT /organizations/me")
    @SuppressWarnings("unused")
    class UpdateMe {

        private void seedAdminUser() {
            Organization org = organizationRepository.save(
                    Organization.builder().name("Old Name").slug("old-name")
                            .subscriptionTier(SubscriptionTier.FREE).build());
            TestTenant.use(org.getId());
            Managers manager = managersRepository.save(
                    Managers.builder().organization(org).firstName("Admin").lastName("User")
                            .email("admin@update.com").organization(org).build());
            Roles role = rolesRepository.findByRole("ADMIN").orElseThrow();
            usersRepository.save(Users.builder().organization(org)
                    .username("update-admin").passwordHash("pwd").verified(true)
                    .role(role).manager(manager).organization(org).build());
        }

        @Test
        @Order(1)
        @WithMockUser(username = "update-admin", roles = "ADMIN")
        @DisplayName("Admin updates organization name; a subscriptionTier in the body is ignored, not self-service")
        void shouldUpdateOrgNameAndTier() throws Exception {
            seedAdminUser();
            mockMvc.perform(put(ME_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"New Name\",\"subscriptionTier\":\"PRO\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Name"))
                    .andExpect(jsonPath("$.subscriptionTier").value("FREE"));
        }

        @Test
        @Order(2)
        @WithMockUser(username = "update-admin", roles = "ADMIN")
        @DisplayName("Partial update (only name) leaves tier unchanged")
        void shouldUpdateOnlyName() throws Exception {
            seedAdminUser();
            mockMvc.perform(put(ME_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Renamed Corp\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Renamed Corp"))
                    .andExpect(jsonPath("$.subscriptionTier").value("FREE"));
        }

        @Test
        @Order(3)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager is denied update access")
        void shouldReturn403ForManager() throws Exception {
            mockMvc.perform(put(ME_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Hack\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Registration — Terms of Service acceptance")
    @SuppressWarnings("unused")
    class TermsAcceptanceEvidence {

        @Test
        @DisplayName("stores the signed acceptance with version, clauses, method, timestamps and origin, and audits it")
        void shouldRecordAcceptance() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("User-Agent", "Chrome/130")
                    .content(registration("Terms Corp", "termsadmin", "admin@termscorp.com")))
                    .andExpect(status().isAccepted());
            String id = pendingRegistrationRepository.findAll().get(0).getPublicId();
            mockMvc.perform(post(CONFIRM_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("User-Agent", "Firefox/131")
                    .content("{\"registrationId\":\"%s\",\"code\":\"%s\"}"
                            .formatted(id, emailedCode("admin@termscorp.com"))))
                    .andExpect(status().isCreated());

            Organization org = organizationRepository.findAll().stream()
                    .filter(o -> "Terms Corp".equals(o.getName())).findFirst().orElseThrow();
            assertEquals(LegalTerms.CURRENT_VERSION, org.getTermsVersion());
            assertNotNull(org.getTermsAcceptedAt());

            List<TermsAcceptance> evidence =
                    termsAcceptanceRepository.findByOrganizationIdOrderByAcceptedAtDesc(org.getId());
            assertEquals(1, evidence.size());
            TermsAcceptance signed = evidence.get(0);
            assertEquals("termsadmin", signed.getUsername());
            assertEquals("admin@termscorp.com", signed.getSignerEmail());
            assertEquals(LegalTerms.CURRENT_VERSION, signed.getTermsVersion());
            assertEquals(LegalTerms.ONEROUS_CLAUSES, signed.getOnerousClauses());
            assertEquals(TermsAcceptance.SIGNATURE_EMAIL_OTP, signed.getSignatureMethod());
            assertFalse(signed.getAcceptedAt().isBefore(signed.getCodeSentAt()));
            assertEquals("Chrome/130", signed.getRequestUserAgent());
            assertEquals("Firefox/131", signed.getConfirmUserAgent());
            assertEquals("127.0.0.1", signed.getConfirmIp());

            assertTrue(auditLogRepository.findAll().stream().anyMatch(log ->
                    "TERMS_ACCEPTED".equals(log.getAction()) && "termsadmin".equals(log.getUsername())
                            && org.getId().equals(log.getOrgId())));
        }

        @ParameterizedTest(name = "terms={0}, professional={1}, clauses={2}")
        @CsvSource({"false, true, true", "true, false, true", "true, true, false"})
        @DisplayName("refuses a registration missing any of the three declarations (400), sending no code")
        void shouldRefuseMissingDeclaration(boolean terms, boolean professional, boolean clauses) throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration("Terms Corp", "termsadmin", "admin@termscorp.com",
                            LegalTerms.CURRENT_VERSION, terms, professional, clauses)))
                    .andExpect(status().isBadRequest());

            assertEquals(0, pendingRegistrationRepository.count());
            verify(dummyEmailService, never()).sendRegistrationCode(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("refuses an acceptance of a Terms version other than the published one (400)")
        void shouldRefuseStaleTermsVersion() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration("Terms Corp", "termsadmin", "admin@termscorp.com",
                            "2020-01-01", true, true, true)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Termini di servizio sono stati aggiornati")));

            assertEquals(0, pendingRegistrationRepository.count());
        }
    }
}
