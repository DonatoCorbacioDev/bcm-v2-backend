package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.donatodev.bcm_backend.entity.WorkflowStage;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.service.PasswordResetTokenService;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;
import com.donatodev.bcm_backend.support.TestTenant;
import com.donatodev.bcm_backend.util.AfterTransaction;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Transactions, rollback and concurrent updates on real MySQL (OWASP Database
 * Security, data integrity and transactions): what H2 and mocks cannot show,
 * that InnoDB's row locks, the version column (V61) and the CHECK constraints
 * (V62) behave as the code expects.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionIntegrityIT extends AbstractMySQLIntegrationTest {

    private static final int SIMULTANEOUS_REQUESTS = 4;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtils jwt;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private PasswordResetTokenService passwordResetTokenService;
    @Autowired private ContractsRepository contractsRepository;

    private UUID orgId;
    private UUID userId;
    private UUID contractId;
    private UUID counterpartyId;
    private UUID areaId;
    private UUID managerId;
    private String adminToken;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        TestTenant.clear();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Organization org = save(Organization.builder().name("Transactions").slug(UUID.randomUUID().toString()).build());
            orgId = org.getId();
            String name = UUID.randomUUID().toString();
            Roles role = em.createQuery("select r from Roles r where r.role = 'ADMIN'", Roles.class)
                    .getResultList().stream().findFirst().orElseGet(() -> save(Roles.builder().role("ADMIN").build()));
            Managers profile = save(Managers.builder().organization(org).firstName("Anna").lastName("Verdi")
                    .email(name + "@example.com").build());
            Users admin = save(Users.builder().organization(org).manager(profile).role(role).username(name)
                    .passwordHash("old-hash").verified(true).build());
            BusinessAreas area = save(BusinessAreas.builder().organization(org).name(name).build());
            Counterparty supplier = save(Counterparty.builder().organization(org).name("Fornitore")
                    .type(CounterpartyType.SUPPLIER).build());
            Contracts contract = save(Contracts.builder().organization(org).businessArea(area).counterparty(supplier)
                    .manager(profile).contractNumber(name).startDate(LocalDate.of(2026, 1, 1))
                    .endDate(LocalDate.of(2026, 12, 31)).status(ContractStatus.DRAFT).build());
            userId = admin.getId();
            contractId = contract.getId();
            counterpartyId = supplier.getId();
            areaId = area.getId();
            managerId = profile.getId();
            adminToken = jwt.generateTokenFromUser(admin);
        });
    }

    @AfterEach
    void clean() {
        TenantContext.clear();
        String org = orgId.toString();
        jdbc.update("DELETE FROM contract_workflow_events WHERE contract_id IN (SELECT id FROM contracts WHERE organization_id = ?)", org);
        jdbc.update("DELETE FROM contract_history WHERE contract_id IN (SELECT id FROM contracts WHERE organization_id = ?)", org);
        jdbc.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE organization_id = ?)", org);
        jdbc.update("DELETE FROM password_reset_token WHERE user_id IN (SELECT id FROM users WHERE organization_id = ?)", org);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE organization_id = ?)", org);
        jdbc.update("DELETE FROM audit_logs WHERE org_id = ?", org);
        for (String table : List.of("contracts", "users", "managers", "counterparties", "business_areas")) {
            jdbc.update("DELETE FROM " + table + " WHERE organization_id = ?", org);
        }
        jdbc.update("DELETE FROM organizations WHERE id = ?", org);
    }

    @Nested
    @DisplayName("concurrent updates (@Version, V61)")
    @SuppressWarnings("unused")
    class ConcurrentUpdates {

        @Test
        @DisplayName("simultaneous approvals of the same contract: one passes, one workflow event")
        void oneApproval() throws Exception {
            jdbc.update("UPDATE contracts SET workflow_stage = 'IN_REVIEW' WHERE id = ?", contractId.toString());

            List<Integer> statuses = simultaneously(() -> mvc.perform(post("/contracts/" + contractId + "/workflow/approve")
                    .header("Authorization", "Bearer " + adminToken)).andReturn().getResponse().getStatus());

            assertThat(statuses.stream().filter(s -> s / 100 == 2)).hasSize(1);
            // The others: refused as conflicting (409), or, when they read after
            // the first had committed, as no longer in review (400).
            assertThat(statuses.stream().filter(s -> s / 100 != 2)).allMatch(s -> s == 409 || s == 400);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contract_workflow_events WHERE contract_id = ? AND action = 'APPROVE'",
                    Integer.class, contractId.toString())).isEqualTo(1);
        }

        @Test
        @DisplayName("simultaneous edits from the same version: one saved, the others refused with 409")
        void oneEdit() throws Exception {
            long version = versionOf(contractId);
            AtomicInteger n = new AtomicInteger();

            List<Integer> statuses = simultaneously(() -> mvc.perform(put("/contracts/" + contractId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contractJson("Progetto " + n.incrementAndGet(), version)))
                    .andReturn().getResponse().getStatus());

            assertThat(statuses).containsOnlyOnce(200);
            assertThat(statuses.stream().filter(s -> s != 200)).allMatch(s -> s == 409);
            assertThat(versionOf(contractId)).isEqualTo(version + 1);
        }

        @Test
        @DisplayName("an edit made from an out-of-date copy is refused (409) and changes nothing")
        void staleEdit() throws Exception {
            String body = mvc.perform(get("/contracts/" + contractId).header("Authorization", "Bearer " + adminToken))
                    .andReturn().getResponse().getContentAsString();
            long read = JSON.readTree(body).path("version").asLong();
            // Someone else saves meanwhile.
            jdbc.update("UPDATE contracts SET project_name = 'Altro', version = version + 1 WHERE id = ?", contractId.toString());

            int status = mvc.perform(put("/contracts/" + contractId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contractJson("Mio", read)))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT project_name FROM contracts WHERE id = ?", String.class,
                    contractId.toString())).isEqualTo("Altro");
        }
    }

    @Nested
    @DisplayName("all or nothing")
    @SuppressWarnings("unused")
    class Atomicity {

        @Test
        @DisplayName("a password reset failing after the link was used up leaves the link valid and the password as it was")
        void resetRolledBack() {
            String link = resetLink();

            assertThatThrownBy(() -> passwordResetTokenService.resetPassword(link, "short"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token WHERE user_id = ?", Integer.class,
                    userId.toString())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class,
                    userId.toString())).isEqualTo("old-hash");
        }

        @Test
        @DisplayName("the same reset link used twice at once changes the password once")
        void resetOnce() throws Exception {
            String link = resetLink();

            List<Integer> outcomes = simultaneously(() -> {
                try {
                    passwordResetTokenService.resetPassword(link, "NewPassword2");
                    return 1;
                } catch (RuntimeException e) {
                    return 0;
                }
            });

            assertThat(outcomes).containsOnlyOnce(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token WHERE user_id = ?", Integer.class,
                    userId.toString())).isZero();
        }
    }

    @Nested
    @DisplayName("what a rollback cannot undo (AfterTransaction)")
    @SuppressWarnings("unused")
    class ExternalEffects {

        @Test
        @DisplayName("an action tied to the commit runs after a commit, never after a rollback")
        void afterCommit() {
            AtomicInteger sent = new AtomicInteger();
            TransactionTemplate tx = new TransactionTemplate(transactions);

            tx.executeWithoutResult(status -> {
                jdbc.update("UPDATE contracts SET project_name = 'Uno' WHERE id = ?", contractId.toString());
                AfterTransaction.afterCommit(sent::incrementAndGet);
                assertThat(sent).hasValue(0);
            });
            assertThat(sent).hasValue(1);

            tx.executeWithoutResult(status -> {
                jdbc.update("UPDATE contracts SET project_name = 'Due' WHERE id = ?", contractId.toString());
                AfterTransaction.afterCommit(sent::incrementAndGet);
                status.setRollbackOnly();
            });
            assertThat(sent).hasValue(1);
            assertThat(jdbc.queryForObject("SELECT project_name FROM contracts WHERE id = ?", String.class,
                    contractId.toString())).isEqualTo("Uno");
        }

        @Test
        @DisplayName("a clean-up tied to the rollback runs after a rollback, never after a commit")
        void onRollback() {
            AtomicInteger cleaned = new AtomicInteger();
            TransactionTemplate tx = new TransactionTemplate(transactions);

            tx.executeWithoutResult(status -> AfterTransaction.onRollback(cleaned::incrementAndGet));
            assertThat(cleaned).hasValue(0);

            tx.executeWithoutResult(status -> {
                AfterTransaction.onRollback(cleaned::incrementAndGet);
                status.setRollbackOnly();
            });
            assertThat(cleaned).hasValue(1);
        }
    }

    @Nested
    @DisplayName("integrity enforced by MySQL itself (V62)")
    @SuppressWarnings("unused")
    class CheckConstraints {

        @Test
        @DisplayName("a contract ending before it starts is refused by the database")
        void contractDates() {
            assertRefusedByCheck(() -> jdbc.update("UPDATE contracts SET end_date = '2025-12-31' WHERE id = ?",
                    contractId.toString()));
        }

        @Test
        @DisplayName("a negative annual value and a failed-attempts count below zero are refused")
        void negativeValues() {
            assertRefusedByCheck(() -> jdbc.update("UPDATE contracts SET annual_value = -1 WHERE id = ?",
                    contractId.toString()));
            assertRefusedByCheck(() -> jdbc.update("UPDATE users SET failed_login_attempts = -1 WHERE id = ?",
                    userId.toString()));
        }

        @Test
        @DisplayName("through JPA, as the API writes, the refusal is a data integrity violation (answered 400)")
        void throughJpa() {
            TransactionTemplate tx = new TransactionTemplate(transactions);

            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                Contracts contract = contractsRepository.findById(contractId).orElseThrow();
                contract.setAnnualValue(-1.0);
                contractsRepository.saveAndFlush(contract);
            })).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_contracts_annual_value");
        }

        /** MySQL's own refusal, error 3819, whatever Spring makes of it. */
        private void assertRefusedByCheck(org.assertj.core.api.ThrowableAssert.ThrowingCallable write) {
            assertThatThrownBy(write).hasRootCauseInstanceOf(java.sql.SQLException.class)
                    .rootCause().hasMessageContaining("Check constraint");
        }
    }

    @Nested
    @DisplayName("quotas under simultaneous requests (organization row lock)")
    @SuppressWarnings("unused")
    class QuotaRaces {

        @Test
        @DisplayName("simultaneous creations with one contract left in the plan: exactly one is created")
        void lastContractSlot() throws Exception {
            int limit = 15; // FREE (TierLimitService)
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                Organization org = em.find(Organization.class, orgId);
                BusinessAreas area = em.find(BusinessAreas.class, areaId);
                Counterparty supplier = em.find(Counterparty.class, counterpartyId);
                Managers profile = em.find(Managers.class, managerId);
                for (int i = 1; i < limit - 1; i++) {
                    save(Contracts.builder().organization(org).businessArea(area).counterparty(supplier)
                            .manager(profile).contractNumber(UUID.randomUUID().toString())
                            .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
                            .status(ContractStatus.DRAFT).build());
                }
            });
            assertThat(contractCount()).isEqualTo(limit - 1);

            List<Integer> statuses = simultaneously(() -> mvc.perform(post("/contracts")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(newContractJson(UUID.randomUUID().toString())))
                    .andReturn().getResponse().getStatus());

            assertThat(statuses.stream().filter(s -> s / 100 == 2)).hasSize(1);
            assertThat(statuses.stream().filter(s -> s / 100 != 2)).allMatch(s -> s == 409);
            assertThat(contractCount()).isEqualTo(limit);
        }
    }

    private int contractCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM contracts WHERE organization_id = ?", Integer.class,
                orgId.toString());
    }

    private String newContractJson(String contractNumber) {
        return """
                {"counterpartyId":"%s","contractNumber":"%s","projectName":"Nuovo","status":"DRAFT",
                 "startDate":"2026-01-01","endDate":"2026-12-31","areaId":"%s","managerId":"%s"}
                """.formatted(counterpartyId, contractNumber, areaId, managerId);
    }

    private String resetLink() {
        Users user = new TransactionTemplate(transactions).execute(tx -> em.find(Users.class, userId));
        return passwordResetTokenService.createToken(user);
    }

    private long versionOf(UUID id) {
        return jdbc.queryForObject("SELECT version FROM contracts WHERE id = ?", Long.class, id.toString());
    }

    private String contractJson(String projectName, long version) {
        return """
                {"counterpartyId":"%s","contractNumber":"%s","projectName":"%s","status":"DRAFT",
                 "startDate":"2026-01-01","endDate":"2026-12-31","areaId":"%s","managerId":"%s","version":%d}
                """.formatted(counterpartyId, contractNumberOf(contractId), projectName, areaId, managerId, version);
    }

    private String contractNumberOf(UUID id) {
        return jdbc.queryForObject("SELECT contract_number FROM contracts WHERE id = ?", String.class, id.toString());
    }

    /** Runs the same call from several threads released together, and returns what each returned. */
    private static <T> List<T> simultaneously(Callable<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(SIMULTANEOUS_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < SIMULTANEOUS_REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private <T> T save(T value) {
        em.persist(value);
        return value;
    }
}
