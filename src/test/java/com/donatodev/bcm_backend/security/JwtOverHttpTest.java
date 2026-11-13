package com.donatodev.bcm_backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.support.TestTenant;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import jakarta.persistence.EntityManager;

/**
 * The JWT checks proven over HTTP, with real tokens through the real filter
 * chain: nothing here is mocked or simulated. The many controller tests that
 * use {@code @WithMockUser} put a principal straight into the security
 * context, so they show what a role may do but never that a token is checked;
 * this class shows the token is: its signature, algorithm, issuer, audience,
 * expiry and the user's last credential change. A signed-in user then still
 * meets the role (403) and organization (404) checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtOverHttpTest {

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JwtUtils jwt;
    @Value("${jwt.secret}") private String secret;

    private Users admin;
    private Users manager;
    private UUID otherOrganizationsManager;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        TestTenant.clear();
        Organization org = organization("JWT");
        admin = user(org, "ADMIN", null);
        manager = user(org, "MANAGER", profile(org));
        otherOrganizationsManager = profile(organization("Other")).getId();
        em.flush();
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        TestTenant.clear();
    }

    @Test
    @DisplayName("a token the backend issued is accepted")
    void issuedTokenIsAccepted() throws Exception {
        call(jwt.generateToken(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(admin.getUsername()));
    }

    @Test
    @DisplayName("no token, or one that isn't a JWT: 401")
    void missingOrGarbageToken() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        call("not-a-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an expired token: 401")
    void expiredToken() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        call(token(admin, past, past.plusSeconds(60)).signWith(key()).compact())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token signed with another key: 401")
    void otherKey() throws Exception {
        SecretKey attackersKey = Jwts.SIG.HS256.key().build();
        call(validToken(admin).signWith(attackersKey).compact()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unsigned token (alg none): 401")
    void unsignedToken() throws Exception {
        call(validToken(admin).compact()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token whose payload was changed after signing: 401")
    void tamperedPayload() throws Exception {
        String[] parts = jwt.generateToken(manager).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace(manager.getUsername(), admin.getUsername());
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        call(forged).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token from another issuer, or with none: 401")
    void wrongOrMissingIssuer() throws Exception {
        call(validToken(admin).issuer("someone-else").signWith(key()).compact())
                .andExpect(status().isUnauthorized());
        call(validToken(admin).issuer(null).signWith(key()).compact()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token meant for another audience, or for none: 401")
    void wrongOrMissingAudience() throws Exception {
        Instant now = Instant.now();
        call(token(admin, now, now.plusSeconds(600), "bcm-ml").signWith(key()).compact())
                .andExpect(status().isUnauthorized());
        call(token(admin, now, now.plusSeconds(600), null).signWith(key()).compact())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the two-factor step tokens open no session: 401")
    void twoFactorStepTokens() throws Exception {
        call(jwt.generateMfaPendingToken(admin)).andExpect(status().isUnauthorized());
        call(jwt.generateMfaSetupToken(admin)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token issued before the user's last credential change: 401")
    void issuedBeforeCredentialChange() throws Exception {
        String token = jwt.generateToken(admin);
        admin.setCredentialsChangedAt(Instant.now().plusSeconds(5).getEpochSecond());
        em.flush();
        call(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a valid token for a manager is refused the administrator's functions: 403")
    void validTokenWrongRole() throws Exception {
        mvc.perform(get("/users").header("Authorization", "Bearer " + jwt.generateToken(manager)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a valid token does not reach another organization's records: 404")
    void validTokenOtherOrganization() throws Exception {
        mvc.perform(get("/managers/{id}", otherOrganizationsManager)
                        .header("Authorization", "Bearer " + jwt.generateToken(admin)))
                .andExpect(status().isNotFound());
    }

    private ResultActions call(String token) throws Exception {
        return mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token));
    }

    private JwtBuilder validToken(Users user) {
        Instant now = Instant.now();
        return token(user, now, now.plusSeconds(600));
    }

    private JwtBuilder token(Users user, Instant issuedAt, Instant expiresAt) {
        return token(user, issuedAt, expiresAt, JwtUtils.AUDIENCE_API);
    }

    private JwtBuilder token(Users user, Instant issuedAt, Instant expiresAt, String audience) {
        JwtBuilder builder = Jwts.builder()
                .issuer(JwtUtils.ISSUER)
                .subject(user.getUsername())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("orgId", user.getOrganization().getId().toString());
        return audience == null ? builder : builder.audience().add(audience).and();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
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

    private Users user(Organization org, String roleName, Managers profile) {
        Roles role = em.createQuery("select r from Roles r where r.role = :role", Roles.class)
                .setParameter("role", roleName).getResultList().stream().findFirst().orElseGet(() -> {
                    Roles created = Roles.builder().role(roleName).build();
                    em.persist(created);
                    return created;
                });
        Users user = Users.builder().organization(org).manager(profile).role(role).verified(true)
                .username("u" + UUID.randomUUID().toString().substring(0, 12)).passwordHash("x").build();
        em.persist(user);
        return user;
    }
}
