package com.donatodev.bcm_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.support.TestTenant;

import jakarta.persistence.EntityManager;

/**
 * OWASP API3 (Broken Object Property Level Authorization), through real JWTs,
 * filters, services and repositories: a request may send any property, but
 * only the ones its caller may set are applied, and a response never carries
 * a property its reader may not see. Each test sends the forbidden property
 * and checks the database, not just the response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PropertyLevelAuthorizationTest {

    private static final String VERIFIED_IBAN = "IT60X0542811101000000123456";
    private static final String OTHER_IBAN = "IT02L1234512345123456789012";

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtils jwt;

    private Organization org;
    private BusinessAreas area;
    private Managers manager;
    private Users managerUser;
    private Users admin;
    private Counterparty counterparty;
    private FinancialTypes type;
    private Contracts contract;
    private String adminToken;
    private String managerToken;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        TestTenant.clear();
        String name = UUID.randomUUID().toString();
        org = persist(Organization.builder().name("API3").slug(name).subscriptionTier(SubscriptionTier.FREE).build());
        area = persist(BusinessAreas.builder().organization(org).name(name).build());
        manager = persist(Managers.builder().organization(org).firstName("Anna").lastName("Verdi").email(name + "@example.com").build());
        managerUser = account(manager, "MANAGER");
        admin = account(null, "ADMIN");
        counterparty = persist(Counterparty.builder().organization(org).name(name).type(CounterpartyType.SUPPLIER)
                .verifiedIban(VERIFIED_IBAN).build());
        type = persist(FinancialTypes.builder().organization(org).name(name).category(FinancialCategory.REVENUE).build());
        contract = persist(Contracts.builder().organization(org).manager(manager).businessArea(area).counterparty(counterparty)
                .contractNumber(name).startDate(LocalDate.of(2026, Month.JANUARY, 1)).endDate(LocalDate.of(2027, Month.JANUARY, 1))
                .status(ContractStatus.ACTIVE).build());
        adminToken = jwt.generateTokenFromUser(admin);
        managerToken = jwt.generateTokenFromUser(managerUser);
        em.flush();
        em.clear();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        TestTenant.clear();
    }

    @Nested
    @DisplayName("A property the caller may not set is ignored")
    class Input {

        @Test
        @DisplayName("verifiedIban on a new counterparty: only the explicit confirmation sets it")
        void newCounterpartyIsNeverVerified() throws Exception {
            String id = jsonId(send(post("/counterparties"), adminToken,
                    "{\"name\":\"Fornitore nuovo\",\"type\":\"SUPPLIER\",\"verifiedIban\":\"" + OTHER_IBAN + "\"}")
                    .andExpect(status().isCreated()));

            assertThat(column("counterparties", "verified_iban", id)).isNull();
        }

        @Test
        @DisplayName("verifiedIban on an update: the verified one stays")
        void updateKeepsTheVerifiedIban() throws Exception {
            send(put("/counterparties/" + counterparty.getId()), adminToken,
                    "{\"name\":\"Rinominato\",\"type\":\"SUPPLIER\",\"verifiedIban\":\"" + OTHER_IBAN + "\"}")
                    .andExpect(status().isOk());

            assertThat(column("counterparties", "verified_iban", counterparty.getId())).isEqualTo(VERIFIED_IBAN);
            assertThat(column("counterparties", "name", counterparty.getId())).isEqualTo("Rinominato");
        }

        @Test
        @DisplayName("source of a financial value sent by a manager: always MANUAL")
        void managerValueIsAlwaysManual() throws Exception {
            String id = jsonId(send(post("/financial-values"), managerToken,
                    "{\"month\":3,\"year\":2026,\"financialAmount\":100,\"contractId\":\"" + contract.getId()
                            + "\",\"financialTypeId\":\"" + type.getId() + "\",\"businessAreaId\":\"" + area.getId()
                            + "\",\"source\":\"GENERATED\"}")
                    .andExpect(status().isCreated()));

            assertThat(column("financial_values", "source", id)).isEqualTo("MANUAL");
        }

        @Test
        @DisplayName("workflowStage and id of a new contract: decided by the server")
        void contractStageAndIdAreTheServers() throws Exception {
            String id = jsonId(send(post("/contracts"), adminToken,
                    "{\"id\":\"" + contract.getId() + "\",\"counterpartyId\":\"" + counterparty.getId()
                            + "\",\"areaId\":\"" + area.getId() + "\",\"contractNumber\":\"NEW-1\",\"status\":\"ACTIVE\""
                            + ",\"workflowStage\":\"APPROVED\",\"startDate\":\"2026-01-01\",\"endDate\":\"2027-01-01\"}")
                    .andExpect(status().isCreated()));

            assertThat(id).isNotEqualTo(contract.getId().toString());
            assertThat(column("contracts", "workflow_stage", id)).isNull();
            assertThat(column("contracts", "contract_number", contract.getId())).isNotEqualTo("NEW-1");
        }

        @Test
        @DisplayName("twoFactorEnabled on a user update: turned on only by the user's own setup")
        void twoFactorIsNeverSetByAnAdmin() throws Exception {
            send(put("/users/" + managerUser.getId()), adminToken,
                    "{\"username\":\"" + managerUser.getUsername() + "\",\"managerId\":\"" + manager.getId()
                            + "\",\"roleId\":\"" + managerUser.getRole().getId() + "\",\"twoFactorEnabled\":true}")
                    .andExpect(status().isOk());

            assertThat(jdbc.queryForObject("SELECT totp_enabled FROM users WHERE id = ?", Boolean.class,
                    managerUser.getId().toString())).isFalse();
        }

        @Test
        @DisplayName("subscriptionTier on the organization: never self-service")
        void organizationCannotUpgradeItself() throws Exception {
            send(put("/organizations/me"), adminToken, "{\"name\":\"API3 rinominata\",\"subscriptionTier\":\"PRO\"}")
                    .andExpect(status().isOk());

            assertThat(column("organizations", "subscription_tier", org.getId())).isEqualTo("FREE");
            assertThat(column("organizations", "name", org.getId())).isEqualTo("API3 rinominata");
        }
    }

    @Nested
    @DisplayName("A property the reader may not see is never sent")
    class Output {

        @Test
        @DisplayName("the user list carries no password, hash or 2FA secret")
        void userListHasNoSecrets() throws Exception {
            String body = mvc.perform(get("/users").header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].password").value(everyItem(nullValue())))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("passwordHash", "test-password", "totpSecret");
        }

        @Test
        @DisplayName("a counterparty's verified IBAN goes out masked")
        void counterpartyIbanIsMasked() throws Exception {
            mvc.perform(get("/counterparties/" + counterparty.getId()).header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verifiedIban").value("IT...3456"));
        }

        @Test
        @DisplayName("an invoice's supplier IBAN goes out masked")
        void invoiceIbanIsMasked() throws Exception {
            persist(ElectronicInvoice.builder().contract(em.find(Contracts.class, contract.getId())).orgId(org.getId())
                    .storagePath(UUID.randomUUID() + ".xml").fileName("fattura.xml").fileSize(1L).contentType("application/xml")
                    .totalAmount(BigDecimal.valueOf(100)).supplierIban(OTHER_IBAN).build());
            em.flush();

            String body = mvc.perform(get("/contracts/" + contract.getId() + "/invoices")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].supplierIban").value("IT...9012"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain(OTHER_IBAN);
        }
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String jsonId(ResultActions result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String column(String table, String column, Object id) {
        em.flush();
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE id = ?", String.class, id.toString());
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private Users account(Managers profile, String role) {
        List<Roles> found = em.createQuery("select r from Roles r where r.role = :role", Roles.class)
                .setParameter("role", role).getResultList();
        Roles r = found.isEmpty() ? persist(Roles.builder().role(role).build()) : found.getFirst();
        return persist(Users.builder().organization(org).manager(profile).username("u" + UUID.randomUUID().toString().substring(0, 12))
                .passwordHash("test-password").verified(true).role(r).build());
    }
}
