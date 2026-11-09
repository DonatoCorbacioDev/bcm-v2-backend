package com.donatodev.bcm_backend.integration;

import java.time.LocalDate;
import java.time.Month;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.*;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real JWT requests and committed fixtures against the production Flyway schema. */
@SpringBootTest
@AutoConfigureMockMvc
class BolaJwtMySQLIT extends AbstractMySQLIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtils jwt;
    @Autowired private PlatformTransactionManager transactions;

    private record Target(Long contractId, Long valueId, Long historyId, Long budgetId) {}
    private final List<Long> orgIds = new ArrayList<>();
    private Target own;
    private Target colleague;
    private Target foreign;
    private String managerToken;
    private String adminToken;

    @BeforeEach
    void seedCommittedData() {
        TenantContext.clear();
        com.donatodev.bcm_backend.support.TestTenant.clear();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Organization org = organization();
            Organization other = organization();
            Users manager = account(org, "MANAGER");
            Users second = account(org, "MANAGER");
            Users outsider = account(other, "MANAGER");
            own = target(org, manager);
            colleague = target(org, second);
            foreign = target(other, outsider);
            managerToken = jwt.generateTokenFromUser(manager);
            adminToken = jwt.generateTokenFromUser(account(org, "ADMIN"));
        });
    }

    @AfterEach
    void cleanOwnFixtures() {
        TenantContext.clear();
        for (Long id : orgIds) {
            jdbc.update("DELETE FROM contract_history WHERE contract_id IN (SELECT id FROM contracts WHERE organization_id = ?)", id);
        }
        for (String table : List.of("audit_logs", "financial_values", "budgets", "contracts", "users", "managers",
                "counterparties", "business_areas", "financial_types", "organizations")) {
            String column = table.equals("organizations") ? "id" : table.equals("audit_logs") ? "org_id" : "organization_id";
            for (Long id : orgIds) jdbc.update("DELETE FROM " + table + " WHERE " + column + " = ?", id);
        }
        orgIds.clear();
    }

    static Stream<Arguments> cases() {
        return Stream.of("contract", "financialList", "historyList", "collaborators", "documents", "invoices",
                "sepa", "workflow", "financialValue", "historyEntry").flatMap(resource ->
                Stream.of("own", "colleague", "foreign").flatMap(owner ->
                        Stream.of(false, true).map(admin -> Arguments.of(resource, owner, admin))));
    }

    @ParameterizedTest(name = "MySQL JWT {0}: {1}, admin={2}")
    @MethodSource("cases")
    void tenantAndOwnershipChecksHoldOnRealMySQL(String resource, String owner, boolean admin) throws Exception {
        Target target = owner.equals("foreign") ? foreign : owner.equals("colleague") ? colleague : own;
        String path = switch (resource) {
            case "contract" -> "/contracts/" + target.contractId();
            case "financialList" -> "/financial-values/by-contract/" + target.contractId();
            case "historyList" -> "/contract-history/contract/" + target.contractId();
            case "financialValue" -> "/financial-values/" + target.valueId();
            case "historyEntry" -> "/contract-history/" + target.historyId();
            case "workflow" -> "/contracts/" + target.contractId() + "/workflow/events";
            case "sepa" -> "/contracts/" + target.contractId() + "/sepa-payments";
            default -> "/contracts/" + target.contractId() + "/" + resource;
        };
        int expected = owner.equals("foreign") ? 404 : owner.equals("colleague") && !admin ? 403 : 200;
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM contracts WHERE id = ?", target.contractId());

        mvc.perform(get(path).header("Authorization", "Bearer " + (admin ? adminToken : managerToken)))
                .andExpect(status().is(expected));

        assertThat(jdbc.queryForMap("SELECT * FROM contracts WHERE id = ?", target.contractId())).isEqualTo(before);
        assertThat(TenantContext.get()).isNull();
    }

    @ParameterizedTest(name = "MySQL create id cannot overwrite: {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"contracts", "financial-values", "contract-history",
            "business-areas", "managers", "counterparties", "financial-types", "budgets", "users"})
    void createBodyIdCannotOverwriteForeignCommittedRow(String resource) throws Exception {
        Map<String, Object> ownContract = jdbc.queryForMap("SELECT * FROM contracts WHERE id = ?", own.contractId());
        Map<String, Object> foreignContract = jdbc.queryForMap("SELECT * FROM contracts WHERE id = ?", foreign.contractId());
        Map<String, Object> ownValue = jdbc.queryForMap("SELECT * FROM financial_values WHERE id = ?", own.valueId());
        Map<String, Object> foreignValue = jdbc.queryForMap("SELECT * FROM financial_values WHERE id = ?", foreign.valueId());
        Object victimId = switch (resource) {
            case "contracts" -> foreign.contractId(); case "financial-values" -> foreign.valueId();
            case "contract-history" -> foreign.historyId(); case "budgets" -> foreign.budgetId();
            case "business-areas" -> foreignContract.get("area_id"); case "managers" -> foreignContract.get("manager_id");
            case "counterparties" -> foreignContract.get("counterparty_id");
            case "financial-types" -> foreignValue.get("financial_type_id");
            case "users" -> jdbc.queryForObject("SELECT modified_by FROM contract_history WHERE id = ?", Long.class, foreign.historyId());
            default -> throw new IllegalArgumentException(resource);
        };
        String table = resource.replace('-', '_');
        if (resource.equals("contract-history")) table = "contract_history";
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", victimId);
        String fields = switch (resource) {
            case "contracts" -> "\"counterpartyId\":" + ownContract.get("counterparty_id")
                    + ",\"areaId\":" + ownContract.get("area_id") + ",\"contractNumber\":\"NEW-CONTRACT\",\"status\":\"ACTIVE\",\"startDate\":\"2026-01-01\",\"endDate\":\"2027-01-01\"";
            case "financial-values" -> "\"contractId\":" + own.contractId() + ",\"financialTypeId\":"
                    + ownValue.get("financial_type_id") + ",\"businessAreaId\":" + ownContract.get("area_id")
                    + ",\"month\":2,\"year\":2026,\"financialAmount\":100";
            case "contract-history" -> "\"contractId\":" + own.contractId()
                    + ",\"modificationDate\":\"2026-01-01T00:00:00\",\"previousStatus\":\"DRAFT\",\"newStatus\":\"ACTIVE\"";
            case "business-areas" -> "\"name\":\"Created area\"";
            case "financial-types" -> "\"name\":\"Created type\",\"category\":\"REVENUE\"";
            case "managers" -> "\"firstName\":\"Created\",\"lastName\":\"Manager\",\"email\":\"" + UUID.randomUUID() + "@example.com\"";
            case "counterparties" -> "\"name\":\"Created counterparty\",\"type\":\"CUSTOMER\"";
            case "budgets" -> "\"businessAreaId\":" + ownContract.get("area_id") + ",\"year\":2027,\"category\":\"REVENUE\",\"targetAmount\":100";
            case "users" -> {
                Long managerId = new TransactionTemplate(transactions).execute(tx -> {
                    Organization owner = em.find(Organization.class, orgIds.getFirst());
                    Managers fresh = save(Managers.builder().organization(owner).firstName("New").lastName("Manager")
                            .email(UUID.randomUUID() + "@example.com").build());
                    return fresh.getId();
                });
                yield "\"username\":\"new-user-" + UUID.randomUUID().toString().substring(0, 12) + "\",\"password\":\"ValidPassword123!\",\"managerId\":"
                        + managerId + ",\"roleId\":" + jdbc.queryForObject("SELECT id FROM roles WHERE role = 'MANAGER'", Long.class);
            }
            default -> throw new IllegalArgumentException(resource);
        };
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/" + resource)
                .header("Authorization", "Bearer " + adminToken).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"id\":" + victimId + "," + fields + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        tools.jackson.databind.JsonNode result = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.getContentAsString());
        assertThat(result.get("id").asLong()).isNotEqualTo(((Number) victimId).longValue());
        assertThat(jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", victimId)).isEqualTo(before);
    }

    private Organization organization() {
        Organization org = save(Organization.builder().name("BOLA MySQL").slug(UUID.randomUUID().toString()).build());
        orgIds.add(org.getId());
        return org;
    }

    private Users account(Organization org, String role) {
        List<Roles> found = em.createQuery("select r from Roles r where r.role = :role", Roles.class)
                .setParameter("role", role).getResultList();
        Roles r = found.isEmpty() ? save(Roles.builder().role(role).build()) : found.getFirst();
        Managers manager = save(Managers.builder().organization(org).firstName("BOLA").lastName("Manager")
                .email(UUID.randomUUID() + "@example.com").build());
        return save(Users.builder().organization(org).manager(manager).role(r).username(UUID.randomUUID().toString())
                .passwordHash("test-password").verified(true).build());
    }

    private Target target(Organization org, Users owner) {
        String name = UUID.randomUUID().toString();
        BusinessAreas area = save(BusinessAreas.builder().organization(org).name(name).build());
        Counterparty cp = save(Counterparty.builder().organization(org).name(name).type(CounterpartyType.CUSTOMER).build());
        FinancialTypes type = save(FinancialTypes.builder().organization(org).name(name).category(FinancialCategory.REVENUE).build());
        Contracts contract = save(Contracts.builder().organization(org).businessArea(area).counterparty(cp).manager(owner.getManager())
                .contractNumber(name).startDate(LocalDate.of(2026, Month.JANUARY,1)).status(ContractStatus.ACTIVE).build());
        FinancialValues value = save(FinancialValues.builder().organization(org).contract(contract).financialType(type).businessArea(area)
                .month(1).year(2026).financialAmount(100).build());
        ContractHistory history = save(ContractHistory.builder().contract(contract).modifiedBy(owner).modificationDate(LocalDateTime.of(2026, Month.JANUARY,1,0,0))
                .previousStatus(ContractStatus.DRAFT).newStatus(ContractStatus.ACTIVE).build());
        Budget budget = save(Budget.builder().organization(org).businessArea(area).category(FinancialCategory.REVENUE)
                .year(2026).targetAmount(100).build());
        return new Target(contract.getId(), value.getId(), history.getId(), budget.getId());
    }

    private <T> T save(T value) { em.persist(value); return value; }
}
