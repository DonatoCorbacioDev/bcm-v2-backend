package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.support.TestTenant;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;

/**
 * OWASP Authorization Cheat Sheet, with real tokens through the real filter
 * chain: every refusal of a function or a record is recorded as a security
 * event, whichever layer refuses it, while an allowed call and another
 * organization's record (404, indistinguishable from a stale link) are not;
 * and an administrator cannot take over another administrator's account by
 * setting its password (horizontal escalation between equals).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthorizationCheatSheetTest {

    private static final String NEW_PASSWORD = "Nuova#Password2026";

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JwtUtils jwt;
    @Autowired private MeterRegistry meters;

    private Users admin;
    private Users otherAdmin;
    private Users manager;
    private UUID otherOrganizationsManager;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        TestTenant.clear();
        Organization org = organization("Authz");
        admin = user(org, "ADMIN");
        otherAdmin = user(org, "ADMIN");
        manager = user(org, "MANAGER");
        otherOrganizationsManager = profile(organization("Other")).getId();
        em.flush();
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        TestTenant.clear();
    }

    @Test
    @DisplayName("a function refused by @PreAuthorize is recorded")
    void preAuthorizeRefusalIsRecorded() throws Exception {
        double before = refusals();
        mvc.perform(get("/users").header("Authorization", bearer(manager))).andExpect(status().isForbidden());
        assertThat(refusals()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("a function refused by the URL rules is recorded too")
    void urlRuleRefusalIsRecorded() throws Exception {
        double before = refusals();
        mvc.perform(get("/actuator/metrics").header("Authorization", bearer(manager))).andExpect(status().isForbidden());
        assertThat(refusals()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("an allowed call and another organization's record (404) are not recorded")
    void allowedCallsAndForeignRecordsAreNotRecorded() throws Exception {
        double before = refusals();
        mvc.perform(get("/users").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/managers/{id}", otherOrganizationsManager).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        assertThat(refusals()).isEqualTo(before);
    }

    @Test
    @DisplayName("an admin cannot set another admin's password (PATCH): 403, password unchanged, recorded")
    void adminCannotPatchAnotherAdminsPassword() throws Exception {
        String hashBefore = otherAdmin.getPasswordHash();
        double before = refusals();

        mvc.perform(patch("/users/{id}", otherAdmin.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());

        em.refresh(otherAdmin);
        assertThat(otherAdmin.getPasswordHash()).isEqualTo(hashBefore);
        assertThat(refusals()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("an admin cannot set another admin's password (PUT) either, nor rename them in the same request")
    void adminCannotPutAnotherAdminsPassword() throws Exception {
        String hashBefore = otherAdmin.getPasswordHash();
        String nameBefore = otherAdmin.getUsername();

        mvc.perform(put("/users/{id}", otherAdmin.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(fullUser(otherAdmin, "renamed1", NEW_PASSWORD)))
                .andExpect(status().isForbidden());

        em.refresh(otherAdmin);
        assertThat(otherAdmin.getPasswordHash()).isEqualTo(hashBefore);
        assertThat(otherAdmin.getUsername()).isEqualTo(nameBefore);
    }

    @Test
    @DisplayName("an admin may still set a manager's password, and another admin's other fields")
    void adminMayStillRecoverManagersAndEditAdmins() throws Exception {
        String managerHash = manager.getPasswordHash();

        mvc.perform(patch("/users/{id}", manager.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/users/{id}", otherAdmin.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(fullUser(otherAdmin, "renamed2", null)))
                .andExpect(status().isOk());

        em.refresh(manager);
        assertThat(manager.getPasswordHash()).isNotEqualTo(managerHash);
    }

    @Test
    @DisplayName("an admin may set their own password from the users page")
    void adminMaySetOwnPassword() throws Exception {
        mvc.perform(patch("/users/{id}", admin.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    private double refusals() {
        return meters.counter("bcm.security.events", "type", "ACCESS_DENIED").count();
    }

    private String bearer(Users user) {
        return "Bearer " + jwt.generateToken(user);
    }

    private static String fullUser(Users user, String username, String password) {
        return "{\"username\":\"" + username + "\",\"managerId\":\"" + user.getManager().getId()
                + "\",\"roleId\":\"" + user.getRole().getId() + "\""
                + (password == null ? "" : ",\"password\":\"" + password + "\"") + "}";
    }

    private Organization organization(String name) {
        Organization org = Organization.builder().name(name).slug(UUID.randomUUID().toString()).build();
        em.persist(org);
        return org;
    }

    private Managers profile(Organization org) {
        Managers profile = Managers.builder().organization(org).firstName("Anna").lastName("Verdi")
                .email(UUID.randomUUID() + "@example.com").build();
        em.persist(profile);
        return profile;
    }

    private Users user(Organization org, String roleName) {
        Roles role = em.createQuery("select r from Roles r where r.role = :role", Roles.class)
                .setParameter("role", roleName).getResultList().stream().findFirst().orElseGet(() -> {
                    Roles created = Roles.builder().role(roleName).build();
                    em.persist(created);
                    return created;
                });
        Users user = Users.builder().organization(org).manager(profile(org)).role(role).verified(true)
                .username("u" + UUID.randomUUID().toString().substring(0, 12)).passwordHash("x").build();
        em.persist(user);
        return user;
    }
}
