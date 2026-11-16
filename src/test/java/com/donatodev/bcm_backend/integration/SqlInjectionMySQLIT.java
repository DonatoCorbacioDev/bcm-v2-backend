package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

import jakarta.persistence.EntityManager;

/**
 * OWASP SQL Injection Prevention Cheat Sheet, through the real endpoints and
 * against real MySQL, with the application's least-privilege account: text
 * that looks like SQL is only ever data, and a LIKE wildcard typed in a search
 * matches only itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Test: SQL injection attempts against real MySQL")
class SqlInjectionMySQLIT extends AbstractMySQLIntegrationTest {

    private static final String[] PAYLOADS = {
            "' OR '1'='1",
            "x'; DROP TABLE contracts; --",
            "%' UNION SELECT password_hash FROM users --",
            "1' AND SLEEP(5) --",
            "\\' OR 1=1 #"};

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtils jwt;
    @Autowired private PlatformTransactionManager transactions;

    private final List<UUID> orgIds = new ArrayList<>();
    private String adminToken;
    private String managerToken;
    private String adminUsername;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        com.donatodev.bcm_backend.support.TestTenant.clear();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Organization org = save(Organization.builder().name("SQLi IT").slug(UUID.randomUUID().toString()).build());
            orgIds.add(org.getId());
            Users admin = account(org, "ADMIN");
            Users manager = account(org, "MANAGER");
            contract(org, manager, "NRT-100", "Alfa Impianti");
            contract(org, manager, "50%_OFF", "Beta Servizi");
            contract(org, admin, "ADM!7", "Gamma Srl");
            adminToken = jwt.generateTokenFromUser(admin);
            managerToken = jwt.generateTokenFromUser(manager);
            adminUsername = admin.getUsername();
        });
    }

    @AfterEach
    void clean() {
        TenantContext.clear();
        for (String table : List.of("audit_logs", "contracts", "users", "managers", "counterparties", "business_areas",
                "organizations")) {
            String column = table.equals("organizations") ? "id" : table.equals("audit_logs") ? "org_id" : "organization_id";
            for (UUID id : orgIds) {
                jdbc.update("DELETE FROM " + table + " WHERE " + column + " = ?", id.toString());
            }
        }
        orgIds.clear();
    }

    @ParameterizedTest(name = "admin and manager search for {0}")
    @DisplayName("text that looks like SQL is searched for as text: 200, nothing found, every row still there")
    @ValueSource(strings = {"' OR '1'='1", "x'; DROP TABLE contracts; --", "%' UNION SELECT password_hash FROM users --",
            "1' AND SLEEP(5) --", "\\' OR 1=1 #"})
    void sqlLookingTextIsJustText(String q) throws Exception {
        int before = contracts();
        for (String token : List.of(adminToken, managerToken)) {
            mvc.perform(get("/contracts/search").param("q", q).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
        assertThat(contracts()).isEqualTo(before);
    }

    @ParameterizedTest(name = "{0} as {1}")
    @DisplayName("a LIKE wildcard or the escape character typed in a search matches only itself")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "%|admin|1", "_|admin|1", "!|admin|1", "NRT|admin|1", "\"\"|admin|3",
            "%|manager|1", "_|manager|1", "!|manager|0", "NRT|manager|1"})
    void wildcardsMatchOnlyThemselves(String q, String who, int found) throws Exception {
        String token = who.equals("admin") ? adminToken : managerToken;
        mvc.perform(get("/contracts/search").param("q", q).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(found));
    }

    @Test
    @DisplayName("SQL in the login form is a wrong name or password, never a way in or an error")
    void sqlInTheLoginIsJustAWrongPassword() throws Exception {
        for (String payload : PAYLOADS) {
            for (String body : List.of(login(payload, "x"), login(adminUsername + "' --", payload),
                    login(adminUsername, payload))) {
                mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Test
    @DisplayName("SQL in the forgot-password form is an invalid or unknown address, never an error")
    void sqlInTheForgotPasswordIsJustAnUnknownAddress() throws Exception {
        for (String payload : PAYLOADS) {
            int answer = mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":" + json(payload + "@example.com") + "}"))
                    .andReturn().getResponse().getStatus();
            // 400 when the address is not one; 200, the answer for any address, when it is.
            assertThat(answer).as(payload).isIn(200, 400);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE organization_id = ?", Integer.class,
                orgIds.get(0).toString())).isEqualTo(2);
    }

    private int contracts() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM contracts", Integer.class);
    }

    private static String login(String username, String password) {
        return "{\"username\":" + json(username) + ",\"password\":" + json(password) + "}";
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Users account(Organization org, String role) {
        List<Roles> found = em.createQuery("select r from Roles r where r.role = :role", Roles.class)
                .setParameter("role", role).getResultList();
        Roles r = found.isEmpty() ? save(Roles.builder().role(role).build()) : found.getFirst();
        Managers manager = save(Managers.builder().organization(org).firstName("SQLi").lastName(role)
                .email(UUID.randomUUID() + "@example.com").build());
        return save(Users.builder().organization(org).manager(manager).role(r).username("sqli" + UUID.randomUUID()
                .toString().substring(0, 12)).passwordHash("{noop}unused").verified(true).build());
    }

    private void contract(Organization org, Users owner, String number, String counterpartyName) {
        BusinessAreas area = save(BusinessAreas.builder().organization(org).name(UUID.randomUUID().toString()).build());
        Counterparty cp = save(Counterparty.builder().organization(org).name(counterpartyName)
                .type(CounterpartyType.CUSTOMER).build());
        save(Contracts.builder().organization(org).businessArea(area).counterparty(cp).manager(owner.getManager())
                .contractNumber(number).startDate(LocalDate.now()).status(ContractStatus.ACTIVE).build());
    }

    private <T> T save(T value) {
        em.persist(value);
        return value;
    }
}
