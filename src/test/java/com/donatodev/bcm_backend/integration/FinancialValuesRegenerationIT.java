package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.FinancialGenerationResultDTO;
import com.donatodev.bcm_backend.entity.BillingFrequency;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.service.ContractFinancialGenerationService;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;
import com.donatodev.bcm_backend.support.TestTenant;

import jakarta.persistence.EntityManager;

/**
 * Regenerating a contract's expected amounts twice. The future GENERATED rows
 * are deleted and created again for the same months, and MySQL holds one row
 * per contract, type, month and year (uq_fv_contract_type_month_year, not on
 * the H2 test schema): Hibernate flushes inserts before deletes, so without
 * flushing the deletes first the second run failed with a duplicate key
 * (found in the interface review, 27/11/2026).
 */
@SpringBootTest
class FinancialValuesRegenerationIT extends AbstractMySQLIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ContractFinancialGenerationService generation;

    private UUID orgId;
    private UUID contractId;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        TestTenant.clear();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Organization org = save(Organization.builder().name("Regen").slug(UUID.randomUUID().toString()).build());
            orgId = org.getId();
            String name = UUID.randomUUID().toString();
            BusinessAreas area = save(BusinessAreas.builder().organization(org).name(name).build());
            Counterparty supplier = save(Counterparty.builder().organization(org).name("Fornitore")
                    .type(CounterpartyType.SUPPLIER).build());
            FinancialTypes type = save(FinancialTypes.builder().organization(org).name(name)
                    .category(FinancialCategory.COST).build());
            // Every month from next one on: all rows are "future", so all are regenerated.
            LocalDate start = YearMonth.now().plusMonths(1).atDay(1);
            Contracts contract = save(Contracts.builder().organization(org).businessArea(area).counterparty(supplier)
                    .contractNumber(name).startDate(start).endDate(start.plusMonths(11))
                    .status(ContractStatus.ACTIVE).financialType(type)
                    .annualValue(12000.0).billingFrequency(BillingFrequency.MONTHLY).build());
            contractId = contract.getId();
        });
    }

    @AfterEach
    void clean() {
        TenantContext.clear();
        String org = orgId.toString();
        jdbc.update("DELETE FROM financial_values WHERE organization_id = ?", org);
        for (String table : List.of("contracts", "counterparties", "business_areas", "financial_types")) {
            jdbc.update("DELETE FROM " + table + " WHERE organization_id = ?", org);
        }
        jdbc.update("DELETE FROM organizations WHERE id = ?", org);
    }

    @Test
    @DisplayName("regenerating twice replaces the future amounts instead of failing on a duplicate")
    void regeneratesTwice() {
        FinancialGenerationResultDTO first = generate();
        FinancialGenerationResultDTO second = generate();

        assertThat(first.created()).isEqualTo(12);
        assertThat(second.regenerated()).isEqualTo(12);
        assertThat(second.created()).isEqualTo(12);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM financial_values WHERE contract_id = ?", Integer.class, contractId.toString());
        assertThat(rows).isEqualTo(12);
    }

    private FinancialGenerationResultDTO generate() {
        return new TransactionTemplate(transactions).execute(tx -> generation.generate(em.find(Contracts.class, contractId)));
    }

    private <T> T save(T value) {
        em.persist(value);
        return value;
    }
}
