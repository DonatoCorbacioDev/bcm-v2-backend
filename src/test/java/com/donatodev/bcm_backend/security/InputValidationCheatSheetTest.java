package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.GlobalExceptionHandler;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.support.TestTenant;

import jakarta.persistence.EntityManager;

/**
 * OWASP Input Validation Cheat Sheet, with real tokens: format, length and
 * range are checked on the server and refused with 400 and the field's
 * message; a business rule (a contract cannot end before it starts) too;
 * a partial update still takes partial input; sorting only on known columns;
 * and text that looks like SQL is just text.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InputValidationCheatSheetTest {

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JwtUtils jwt;

    private Users admin;
    private Managers profile;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        TestTenant.clear();
        Organization org = Organization.builder().name("Input").slug(UUID.randomUUID().toString()).build();
        em.persist(org);
        profile = Managers.builder().organization(org).firstName("Anna").lastName("Verdi")
                .email(UUID.randomUUID() + "@example.com").build();
        em.persist(profile);
        Roles role = em.createQuery("select r from Roles r where r.role = 'ADMIN'", Roles.class)
                .getResultList().stream().findFirst().orElseGet(() -> {
                    Roles created = Roles.builder().role("ADMIN").build();
                    em.persist(created);
                    return created;
                });
        admin = Users.builder().organization(org).manager(profile).role(role).verified(true)
                .username("u" + UUID.randomUUID().toString().substring(0, 12)).passwordHash("x").build();
        em.persist(admin);
        em.flush();
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        TestTenant.clear();
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("a manager with a wrong format, too long a value or a missing field: 400 with the field's message")
    @CsvSource(delimiter = '|', value = {
            "email not an email   | {\"firstName\":\"Anna\",\"lastName\":\"Rossi\",\"email\":\"abc\"}                  | Email non valida",
            "first name too long  | {\"firstName\":\"%s\",\"lastName\":\"Rossi\",\"email\":\"a@example.com\"}          | non può superare 50",
            "first name missing   | {\"lastName\":\"Rossi\",\"email\":\"a@example.com\"}                              | Nome obbligatorio",
    })
    void managerInputIsChecked(String scenario, String body, String message) throws Exception {
        send(post("/managers"), body.formatted("x".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(message)));
    }

    @Test
    @DisplayName("a partial update of a manager still takes only the fields it changes")
    void partialUpdateTakesPartialInput() throws Exception {
        send(patch("/managers/{id}", profile.getId()), "{\"phoneNumber\":\"0123456\"}").andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("an out-of-range month, year or amount: 400")
    @CsvSource(delimiter = '|', value = {
            "month 13    | {\"month\":13,\"year\":2026,\"financialAmount\":10}       | Il mese va da 1 a 12",
            "month 0     | {\"month\":0,\"year\":2026,\"financialAmount\":10}        | Il mese va da 1 a 12",
            "year 1900   | {\"month\":5,\"year\":1900,\"financialAmount\":10}        | Anno non valido",
            "huge amount | {\"month\":5,\"year\":2026,\"financialAmount\":1e15}      | Importo troppo grande",
    })
    void financialValueRanges(String scenario, String body, String message) throws Exception {
        send(post("/financial-values"), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(message)));
    }

    @Test
    @DisplayName("an area name longer than its column: 400, not a 409 from the database")
    void areaNameTooLong() throws Exception {
        send(post("/business-areas"), "{\"name\":\"" + "x".repeat(256) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("non può superare 255")));
    }

    @Test
    @DisplayName("a contract cannot end before it starts: 400")
    void contractEndBeforeStart() throws Exception {
        send(post("/contracts"), contract("2026-06-01", "2026-05-31", "WBS-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La data di fine non può precedere la data di inizio"));
    }

    @Test
    @DisplayName("a WBS code longer than its column: 400")
    void wbsCodeTooLong() throws Exception {
        send(post("/contracts"), contract("2026-01-01", "2026-12-31", "W".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("non può superare 50")));
    }

    @Test
    @DisplayName("a risk level outside HIGH, MEDIUM, LOW: 400")
    void riskLevelIsChecked() throws Exception {
        send(post("/risk-feedback/contracts/{id}", UUID.randomUUID()),
                "{\"riskScore\":0.5,\"level\":\"<script>\",\"agree\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Livello di rischio non valido")));
    }

    @ParameterizedTest(name = "sort={0}")
    @DisplayName("the audit sorts only by its known columns")
    @CsvSource({"passwordHash", "details", "orgId"})
    void auditSortIsRestricted(String property) throws Exception {
        mvc.perform(get("/audit-logs").param("sort", property).header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Ordinamento non consentito")));
    }

    @Test
    @DisplayName("the audit still sorts by time, and refuses a page too large")
    void auditSortAndPageSize() throws Exception {
        mvc.perform(get("/audit-logs").param("sort", "timestamp,desc").header("Authorization", bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/audit-logs").param("size", "5000").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "q={0}")
    @DisplayName("text that looks like SQL is searched for as text: 200, nothing found, no error")
    @ValueSource(strings = {"' OR '1'='1", "x'; DROP TABLE contracts; --", "%' UNION SELECT password_hash FROM users --"})
    void sqlLookingTextIsJustText(String q) throws Exception {
        mvc.perform(get("/contracts/search").param("q", q).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("a value the database refuses still answers 400 when it is the input's fault, 409 only for a duplicate")
    @CsvSource(delimiter = '|', value = {
            "Value too long for column \"NAME\"           | 400 | Un valore supera la lunghezza consentita",
            "Data too long for column 'wbs_code' at row 1 | 400 | Un valore supera la lunghezza consentita",
            "Column 'name' cannot be null                 | 400 | Manca un campo obbligatorio",
            "NULL not allowed for column \"NAME\"          | 400 | Manca un campo obbligatorio",
            "Duplicate entry 'C-1' for key 'contract_number' | 409 | Conflitto",
    })
    void databaseRefusals(String cause, int status, String message) {
        var response = new GlobalExceptionHandler().handleDataIntegrityViolation(
                new DataIntegrityViolationException("refused", new RuntimeException(cause)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.valueOf(status));
        assertThat(response.getBody().message()).startsWith(message).doesNotContain("wbs_code", "NAME", "contract_number");
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body) throws Exception {
        return mvc.perform(request.header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bearer() {
        return "Bearer " + jwt.generateToken(admin);
    }

    private static String contract(String start, String end, String wbs) {
        return "{\"counterpartyId\":\"" + UUID.randomUUID() + "\",\"areaId\":\"" + UUID.randomUUID()
                + "\",\"contractNumber\":\"C-" + UUID.randomUUID().toString().substring(0, 8)
                + "\",\"wbsCode\":\"" + wbs + "\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}";
    }
}
