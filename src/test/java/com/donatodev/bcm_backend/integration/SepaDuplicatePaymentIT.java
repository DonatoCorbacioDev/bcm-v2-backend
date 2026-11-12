package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;
import com.donatodev.bcm_backend.support.TestTenant;

import jakarta.persistence.EntityManager;

/**
 * OWASP API6 (duplicate operations): requests that pay the same invoice at
 * the same moment, a double click or two colleagues at once, produce one SEPA
 * file, never two that would pay the supplier twice. Real MySQL, because the
 * protection is the row lock InnoDB takes on the invoices.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SepaDuplicatePaymentIT extends AbstractMySQLIntegrationTest {

    private static final int SIMULTANEOUS_REQUESTS = 4;

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtils jwt;
    @Autowired private PlatformTransactionManager transactions;

    private UUID orgId;
    private UUID contractId;
    private UUID invoiceId;
    private String adminToken;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        TestTenant.clear();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Organization org = save(Organization.builder().name("API6 SEPA").slug(UUID.randomUUID().toString())
                    .iban("DE89370400440532013000").bic("COBADEFFXXX").build());
            orgId = org.getId();
            String name = UUID.randomUUID().toString();
            Roles role = em.createQuery("select r from Roles r where r.role = 'ADMIN'", Roles.class)
                    .getResultList().stream().findFirst().orElseGet(() -> save(Roles.builder().role("ADMIN").build()));
            Managers profile = save(Managers.builder().organization(org).firstName("Anna").lastName("Verdi")
                    .email(name + "@example.com").build());
            Users admin = save(Users.builder().organization(org).manager(profile).role(role).username(name)
                    .passwordHash("x").verified(true).build());
            BusinessAreas area = save(BusinessAreas.builder().organization(org).name(name).build());
            Counterparty supplier = save(Counterparty.builder().organization(org).name("Fornitore")
                    .type(CounterpartyType.SUPPLIER).build());
            Contracts contract = save(Contracts.builder().organization(org).businessArea(area).counterparty(supplier)
                    .manager(profile).contractNumber(name).startDate(LocalDate.of(2026, 1, 1))
                    .status(ContractStatus.ACTIVE).build());
            ElectronicInvoice invoice = save(ElectronicInvoice.builder().contract(contract).orgId(org.getId())
                    .storagePath(name + ".xml").fileName("fattura.xml").fileSize(1L).contentType("application/xml")
                    .invoiceNumber("FT-1").supplierVatNumber("IT01234567890").supplierName("Fornitore")
                    .supplierIban("IT60X0542811101000000123456").currency("EUR")
                    .totalAmount(new BigDecimal("122.00")).build());
            contractId = contract.getId();
            invoiceId = invoice.getId();
            adminToken = jwt.generateTokenFromUser(admin);
        });
    }

    @AfterEach
    void clean() {
        TenantContext.clear();
        String org = orgId.toString();
        jdbc.update("DELETE FROM electronic_invoices WHERE org_id = ?", org);
        jdbc.update("DELETE FROM sepa_payment_batches WHERE org_id = ?", org);
        jdbc.update("DELETE FROM audit_logs WHERE org_id = ?", org);
        for (String table : List.of("contracts", "users", "managers", "counterparties", "business_areas")) {
            jdbc.update("DELETE FROM " + table + " WHERE organization_id = ?", org);
        }
        jdbc.update("DELETE FROM organizations WHERE id = ?", org);
    }

    @Test
    @DisplayName("simultaneous requests for the same invoice: one SEPA file, the others refused")
    void oneFileForSimultaneousRequests() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(SIMULTANEOUS_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> responses = new ArrayList<>();
        try {
            for (int i = 0; i < SIMULTANEOUS_REQUESTS; i++) {
                responses.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return mvc.perform(post("/contracts/" + contractId + "/sepa-payments")
                                    .header("Authorization", "Bearer " + adminToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"invoiceIds\":[\"" + invoiceId + "\"]}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsOnlyOnce(201);
            assertThat(statuses).filteredOn(s -> s != 201).allMatch(s -> s == 400);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sepa_payment_batches WHERE org_id = ?",
                Integer.class, orgId.toString())).isEqualTo(1);
        // Each refused duplicate is on record for the weekly report.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE org_id = ? AND action = ?",
                Integer.class, orgId.toString(), "SECURITY_DUPLICATE_PAYMENT_REFUSED"))
                .isEqualTo(SIMULTANEOUS_REQUESTS - 1);
    }

    @Test
    @DisplayName("a payment to a changed IBAN is refused, and the record of it survives the rollback")
    void ibanMismatchIsRecorded() throws Exception {
        jdbc.update("UPDATE counterparties SET verified_iban = ? WHERE organization_id = ?",
                "DE89370400440532013000", orgId.toString());

        int status = mvc.perform(post("/contracts/" + contractId + "/sepa-payments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceIds\":[\"" + invoiceId + "\"]}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sepa_payment_batches WHERE org_id = ?",
                Integer.class, orgId.toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE org_id = ? AND action = ?",
                Integer.class, orgId.toString(), "SEPA_IBAN_MISMATCH_BLOCKED")).isEqualTo(1);
    }

    private <T> T save(T value) {
        em.persist(value);
        return value;
    }
}
