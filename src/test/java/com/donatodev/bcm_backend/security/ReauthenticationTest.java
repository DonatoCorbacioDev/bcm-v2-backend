/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.donatodev.bcm_backend.auth.ReauthenticationService;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * The operations a stolen session would aim at ask for the password again
 * (OWASP ASVS 7.5.1): exporting the organization, changing the email a reset
 * goes to. Without a fresh confirmation they answer 428; the confirmation is a
 * five-minute token of this user only, which opens no session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Administrators need 2FA before a session (TwoFactorPolicy); these two are
// exempt, as the demo's internal admin is in production.
@TestPropertySource(properties = "app.security.two-factor-exempt-usernames=" + ReauthenticationTest.ADMIN + ","
        + ReauthenticationTest.COLLEAGUE)
class ReauthenticationTest {

    static final String ADMIN = "reauth-admin";
    static final String COLLEAGUE = "reauth-colleague";

    private static final String PASSWORD = "Faro-Mareggiata-58";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsersRepository users;
    @Autowired private RolesRepository roles;
    @Autowired private ManagersRepository managers;
    @Autowired private OrganizationRepository organizations;
    @Autowired private PasswordEncoder passwordEncoder;

    private Users admin;
    private Users colleague;
    private String session;

    @BeforeEach
    void seed() throws Exception {
        Organization org = organizations.save(Organization.builder().name("Reauth")
                .slug("reauth-" + UUID.randomUUID()).build());
        Roles role = roles.findByRole("ADMIN").orElseGet(() -> roles.save(Roles.builder().role("ADMIN").build()));
        admin = user(org, role, ADMIN);
        colleague = user(org, role, COLLEAGUE);
        session = login(admin.getUsername());
    }

    @Test
    @DisplayName("the export without a fresh confirmation answers 428, with it 200")
    void exportAsksForThePassword() throws Exception {
        mvc.perform(get("/organizations/me/export").header("Authorization", "Bearer " + session))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.message").value("Per questa operazione conferma la tua password"));

        String reauth = confirm(session, PASSWORD);
        mvc.perform(get("/organizations/me/export").header("Authorization", "Bearer " + session)
                        .header(ReauthenticationService.HEADER, reauth))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a wrong password is a 400, not a 401 that would end the session, and gives no token")
    void wrongPassword() throws Exception {
        mvc.perform(post("/auth/reauth").header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"not-the-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password o codice non corretti"));
        assertThat(users.findById(admin.getId()).orElseThrow().getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("a confirmation is only its own user's, and opens no session")
    void confirmationIsNotASession() throws Exception {
        String reauth = confirm(session, PASSWORD);
        // Not a session: as a bearer token it authenticates nobody.
        mvc.perform(get("/organizations/me").header("Authorization", "Bearer " + reauth))
                .andExpect(status().isUnauthorized());
        // Not someone else's: a colleague's session with this confirmation still asks.
        String other = login(colleague.getUsername());
        mvc.perform(get("/organizations/me/export").header("Authorization", "Bearer " + other)
                        .header(ReauthenticationService.HEADER, reauth))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    @DisplayName("changing the email a password reset goes to asks for the password; other fields do not")
    void emailChangeAsksForThePassword() throws Exception {
        Managers profile = colleague.getManager();
        String sameEmail = managerJson(profile, "Altro nome", profile.getEmail());
        mvc.perform(put("/managers/" + profile.getId()).header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON).content(sameEmail))
                .andExpect(status().isOk());

        String newEmail = managerJson(profile, "Altro nome", "attacker@mailbox.example");
        mvc.perform(put("/managers/" + profile.getId()).header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON).content(newEmail))
                .andExpect(status().isPreconditionRequired());
        assertThat(managers.findById(profile.getId()).orElseThrow().getEmail()).isEqualTo(profile.getEmail());

        mvc.perform(put("/managers/" + profile.getId()).header("Authorization", "Bearer " + session)
                        .header(ReauthenticationService.HEADER, confirm(session, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(newEmail))
                .andExpect(status().isOk());
    }

    private Users user(Organization org, Roles role, String name) {
        Managers profile = managers.save(Managers.builder().organization(org).firstName("Anna").lastName("Verdi")
                .email(name + "-" + UUID.randomUUID().toString().substring(0, 6) + "@studio.example").build());
        // The names are fixed (they are exempt from 2FA above): the previous test's account is reused.
        Users user = users.findByUsername(name).orElseGet(() -> Users.builder().username(name).build());
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setVerified(true);
        user.setOrganization(org);
        user.setManager(profile);
        user.setRole(role);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return users.save(user);
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private String confirm(String bearer, String password) throws Exception {
        String body = mvc.perform(post("/auth/reauth").header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("reauthToken").asText();
    }

    private static String managerJson(Managers profile, String firstName, String email) {
        return """
                {"id":"%s","firstName":"%s","lastName":"Verdi","email":"%s"}
                """.formatted(profile.getId(), firstName, email);
    }
}
