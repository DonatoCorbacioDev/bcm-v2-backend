package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.support.TestTenant;

import jakarta.persistence.EntityManager;

/**
 * OWASP API5 (Broken Function Level Authorization): every function is open
 * only to the role it is meant for. Denied by default: an endpoint without
 * an explicit {@code @PreAuthorize} fails this test unless it is listed below
 * with the reason it needs none.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FunctionLevelAuthorizationTest {

    /**
     * The endpoints with no role of their own. Each one is either public by
     * nature (it is how one signs in or is called by a third party, and has
     * its own protection) or acts on the caller's own account only, behind
     * {@code anyRequest().authenticated()}.
     */
    private static final Set<String> NO_ROLE_BY_DESIGN = Set.of(
            // Signing in, and what comes before a session: rate limited, see SecurityConfig.
            "AuthController.login", "AuthController.verifyTwoFactor", "AuthController.enrollSetup",
            "AuthController.enrollConfirm", "AuthController.refresh", "AuthController.logout",
            "AuthController.forgotPassword", "AuthController.resetPassword", "AuthController.getCurrentUser",
            "AuthController.completeInvite",
            "OrganizationController.register", "OrganizationController.resendRegistrationCode",
            "OrganizationController.confirmRegistration",
            // Third parties: Stripe signs its calls, calendar apps carry a secret token in the path.
            "BillingController.webhook", "CalendarFeedController.getIcsFeed",
            // Ops only: a shared key (InternalApiKeyGuard), and the reverse proxy refuses it from outside.
            "InternalOrganizationController.changeTier",
            // The caller's own account.
            "CalendarFeedController.getFeedUrl", "CalendarFeedController.regenerateFeedUrl",
            "TwoFactorAuthController.status", "TwoFactorAuthController.setup",
            "TwoFactorAuthController.confirm", "TwoFactorAuthController.disable");

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JwtUtils jwt;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    private String managerToken;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        TestTenant.clear();
        Organization org = Organization.builder().name("API5").slug(UUID.randomUUID().toString()).build();
        em.persist(org);
        Managers profile = Managers.builder().organization(org).firstName("Anna").lastName("Verdi")
                .email(UUID.randomUUID() + "@example.com").build();
        em.persist(profile);
        Roles role = em.createQuery("select r from Roles r where r.role = 'MANAGER'", Roles.class)
                .getResultList().stream().findFirst().orElseGet(() -> {
                    Roles created = Roles.builder().role("MANAGER").build();
                    em.persist(created);
                    return created;
                });
        Users manager = Users.builder().organization(org).manager(profile).role(role).verified(true)
                .username("m" + UUID.randomUUID().toString().substring(0, 12)).passwordHash("x").build();
        em.persist(manager);
        em.flush();
        managerToken = jwt.generateTokenFromUser(manager);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        TestTenant.clear();
    }

    @Test
    @DisplayName("every endpoint declares its role, or is listed with the reason it needs none")
    void deniedByDefault() {
        Set<String> withoutRole = new TreeSet<>();
        Set<String> all = new TreeSet<>();
        for (HandlerMethod handler : mappings.getHandlerMethods().values()) {
            if (!handler.getBeanType().getPackageName().startsWith("com.donatodev")) {
                continue;
            }
            String name = handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName();
            all.add(name);
            boolean declared = handler.hasMethodAnnotation(PreAuthorize.class)
                    || handler.getBeanType().isAnnotationPresent(PreAuthorize.class);
            if (!declared) {
                withoutRole.add(name);
            }
        }

        Set<String> unexpected = new TreeSet<>(withoutRole);
        unexpected.removeAll(NO_ROLE_BY_DESIGN);
        assertThat(unexpected).as("endpoints open to any logged-in user without a declared role").isEmpty();

        // A listed endpoint that gained a role, or no longer exists, leaves the list.
        Set<String> stale = new TreeSet<>(NO_ROLE_BY_DESIGN);
        stale.removeAll(withoutRole);
        assertThat(stale).as("entries to remove from NO_ROLE_BY_DESIGN").isEmpty();
        assertThat(all).hasSizeGreaterThan(100);
    }

    @ParameterizedTest(name = "{0} {1}")
    @DisplayName("a manager is refused the administrator's functions")
    @CsvSource({
            "GET,    /users",
            "DELETE, /users/{id}",
            "POST,   /users/{id}/force-reset",
            "GET,    /audit-logs",
            "GET,    /contracts",
            "DELETE, /contracts/{id}",
            "POST,   /contracts/expire-overdue",
            "GET,    /contracts/import/template",
            "DELETE, /managers/{id}",
            "DELETE, /counterparties/{id}",
            "GET,    /organizations/me",
            "GET,    /organizations/me/export",
            "DELETE, /organizations/me/deletion",
    })
    void managerIsRefusedAdminFunctions(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path.replace("{id}", UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("the actuator beyond health is closed to every user")
    @CsvSource({"/actuator/metrics", "/actuator/info", "/actuator/metrics/http.server.requests"})
    void actuatorIsClosed(String path) throws Exception {
        mvc.perform(request(HttpMethod.GET, path).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("health stays open: UptimeRobot polls it without a token")
    void healthStaysOpen() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/actuator/health")).andExpect(status().isOk());
    }
}
