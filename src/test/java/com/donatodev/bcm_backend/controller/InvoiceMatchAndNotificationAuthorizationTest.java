package com.donatodev.bcm_backend.controller;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;
import com.donatodev.bcm_backend.entity.Notification;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.NotificationRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.support.TestTenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real service and repository checks: inaccessible IDs must not reveal existence or change data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvoiceMatchAndNotificationAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private OrganizationRepository organizations;
    @Autowired private RolesRepository roles;
    @Autowired private UsersRepository users;
    @Autowired private NotificationRepository notifications;
    @Autowired private ContractsRepository contracts;
    @Autowired private CounterpartiesRepository counterparties;
    @Autowired private BusinessAreasRepository areas;
    @Autowired private FinancialTypesRepository types;
    @Autowired private FinancialValuesRepository values;
    @Autowired private ElectronicInvoiceRepository invoices;

    private record InvoiceState(InvoiceMatchStatus status, UUID valueId, Double confidence,
                                Instant matchedAt, UUID matchedBy) {}

    @AfterEach
    void clearTenant() {
        TestTenant.clear();
        TenantContext.clear();
    }

    static Stream<Arguments> notificationCases() {
        return Stream.of("ADMIN", "MANAGER").flatMap(role -> Stream.of(
                Arguments.of(role, "own", 204),
                Arguments.of(role, "alreadyRead", 204),
                Arguments.of(role, "colleague", 404),
                Arguments.of(role, "foreignTenant", 404),
                Arguments.of(role, "ownForeignOrg", 404),
                Arguments.of(role, "missing", 404),
                Arguments.of(role, "missingTenant", 403)));
    }

    @ParameterizedTest(name = "notification {0}: {1} -> {2}")
    @MethodSource("notificationCases")
    void shouldHideInaccessibleNotificationsWithoutMarkingThemRead(String role, String scenario, int expectedStatus)
            throws Exception {
        Organization org = organization();
        Organization otherOrg = organization();
        Users caller = account(org, role);
        Users colleague = account(org, role);
        Users outsider = account(otherOrg, role);
        Notification own = notification(caller, org);
        Notification anotherUsers = notification(colleague, org);
        Notification foreign = notification(outsider, otherOrg);
        Notification wrongOrg = notification(caller, otherOrg);
        if (scenario.equals("alreadyRead")) {
            own.setRead(true);
        }
        UUID targetId = switch (scenario) {
            case "colleague" -> anotherUsers.getId();
            case "foreignTenant" -> foreign.getId();
            case "ownForeignOrg" -> wrongOrg.getId();
            case "missing" -> uid(Long.MAX_VALUE);
            default -> own.getId();
        };
        flushAndClear();
        TestTenant.use(org.getId());
        if (scenario.equals("missingTenant")) {
            clearTenant();
        }

        var response = mockMvc.perform(patch("/notifications/{id}/read", targetId)
                .with(user(caller.getUsername()).roles(role))).andExpect(status().is(expectedStatus));
        if (expectedStatus == 404) {
            response.andExpect(jsonPath("$.message").value("Notifica non trovata: " + targetId));
        }

        flushAndClear();
        assertThat(notifications.findById(own.getId()).orElseThrow().isRead()).isEqualTo(expectedStatus == 204);
        for (Notification inaccessible : new Notification[] {anotherUsers, foreign, wrongOrg}) {
            assertThat(notifications.findById(inaccessible.getId()).orElseThrow().isRead()).isFalse();
        }
    }

    @ParameterizedTest(name = "invoice match: {0} -> {1}")
    @CsvSource({"manual,200", "suggested,200", "foreignManual,404", "foreignSuggested,404",
            "missing,404", "otherContractManual,400", "otherContractSuggested,400", "noCandidate,400",
            "alreadyConfirmedElsewhere,400", "missingTenant,403", "managerRole,403"})
    void shouldScopeBothManualAndSuggestedFinancialValues(String scenario, int expectedStatus) throws Exception {
        Organization org = organization();
        Organization otherOrg = organization();
        Users caller = account(org, "ADMIN");
        Contracts local = contract(org);
        FinancialValues own = value(org, local);
        FinancialValues foreign = value(otherOrg, contract(otherOrg));
        FinancialValues otherContract = value(org, contract(org));
        ElectronicInvoice invoice = invoice(local, own);
        ElectronicInvoice foreignInvoice = invoice(foreign.getContract(), foreign);
        UUID overrideId = own.getId();
        switch (scenario) {
            case "suggested" -> overrideId = null;
            case "foreignManual" -> overrideId = foreign.getId();
            case "foreignSuggested" -> {
                invoice.setMatchedFinancialValue(foreign);
                overrideId = null;
            }
            case "otherContractManual" -> overrideId = otherContract.getId();
            case "otherContractSuggested" -> {
                invoice.setMatchedFinancialValue(otherContract);
                overrideId = null;
            }
            case "missing" -> overrideId = uid(Long.MAX_VALUE);
            case "noCandidate" -> {
                invoice.setMatchedFinancialValue(null);
                invoice.setMatchStatus(InvoiceMatchStatus.UNMATCHED);
                overrideId = null;
            }
            case "alreadyConfirmedElsewhere" -> {
                ElectronicInvoice confirmed = invoice(local, own);
                confirmed.setMatchStatus(InvoiceMatchStatus.CONFIRMED);
            }
            default -> { /* Keep the valid manual candidate for authorization checks. */ }
        }
        flushAndClear();
        InvoiceState before = invoiceState(invoice.getId());
        InvoiceState foreignBefore = invoiceState(foreignInvoice.getId());
        TestTenant.use(org.getId());
        if (scenario.equals("missingTenant")) {
            clearTenant();
        }
        String body = overrideId == null ? "" : "{\"financialValueId\":" + q(overrideId) + "}";
        var response = mockMvc.perform(post("/contracts/{contractId}/invoices/{invoiceId}/match/confirm",
                        local.getId(), invoice.getId())
                .with(user(caller.getUsername()).roles(scenario.equals("managerRole") ? "MANAGER" : "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 404) {
            UUID rejectedId = overrideId != null ? overrideId : foreign.getId();
            response.andExpect(jsonPath("$.message").value("Valore finanziario non trovato: " + rejectedId));
        }

        flushAndClear();
        InvoiceState after = invoiceState(invoice.getId());
        if (expectedStatus == 200) {
            assertThat(after.status()).isEqualTo(InvoiceMatchStatus.CONFIRMED);
            assertThat(after.valueId()).isEqualTo(own.getId());
            assertThat(after.matchedBy()).isEqualTo(caller.getId());
            assertThat(after.matchedAt()).isNotNull();
        } else {
            assertThat(after).isEqualTo(before);
        }
        assertThat(invoiceState(foreignInvoice.getId())).isEqualTo(foreignBefore);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private InvoiceState invoiceState(UUID invoiceId) {
        ElectronicInvoice invoice = invoices.findById(invoiceId).orElseThrow();
        return new InvoiceState(invoice.getMatchStatus(),
                invoice.getMatchedFinancialValue() == null ? null : invoice.getMatchedFinancialValue().getId(),
                invoice.getMatchConfidence(), invoice.getMatchedAt(),
                invoice.getMatchedByUser() == null ? null : invoice.getMatchedByUser().getId());
    }

    private Organization organization() {
        return organizations.save(Organization.builder().name("Authorization test")
                .slug(UUID.randomUUID().toString()).build());
    }

    private Users account(Organization org, String role) {
        Roles userRole = roles.findByRole(role).orElseGet(() -> roles.save(Roles.builder().role(role).build()));
        return users.save(Users.builder().organization(org).username(UUID.randomUUID().toString())
                .passwordHash("test-password").verified(true).role(userRole).build());
    }

    private Notification notification(Users owner, Organization org) {
        return notifications.save(Notification.builder().user(owner).orgId(org.getId())
                .title("Test notification").message("Test message").build());
    }

    private Contracts contract(Organization org) {
        BusinessAreas area = areas.save(BusinessAreas.builder().organization(org).name(UUID.randomUUID().toString()).build());
        Counterparty counterparty = counterparties.save(Counterparty.builder().organization(org)
                .name("Counterparty").type(CounterpartyType.CUSTOMER).build());
        return contracts.save(Contracts.builder().organization(org).businessArea(area).counterparty(counterparty)
                .contractNumber(UUID.randomUUID().toString()).startDate(LocalDate.of(2026, Month.JANUARY, 1))
                .status(ContractStatus.ACTIVE).build());
    }

    private FinancialValues value(Organization org, Contracts contract) {
        FinancialTypes type = types.save(FinancialTypes.builder().organization(org).name(UUID.randomUUID().toString())
                .category(FinancialCategory.REVENUE).build());
        return values.save(FinancialValues.builder().organization(org).contract(contract)
                .businessArea(contract.getBusinessArea()).financialType(type).month(1).year(2026)
                .financialAmount(100).build());
    }

    private ElectronicInvoice invoice(Contracts contract, FinancialValues candidate) {
        return invoices.save(ElectronicInvoice.builder().contract(contract).orgId(contract.getOrganization().getId())
                .storagePath(UUID.randomUUID().toString()).fileName("invoice.xml").fileSize(100L)
                .contentType("application/xml").totalAmount(BigDecimal.valueOf(100))
                .matchedFinancialValue(candidate).matchStatus(InvoiceMatchStatus.SUGGESTED).matchConfidence(0.8).build());
    }

    /** A value as JSON: ids are UUIDs now, so they go in quotes (ADR-0009). */
    private static String q(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
