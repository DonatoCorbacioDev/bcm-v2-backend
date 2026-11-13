package com.donatodev.bcm_backend.controller;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractManager;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractManagerRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.support.TestTenant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises both resource endpoints through real services and persisted authorization data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContractResourceAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizations;
    @Autowired private ManagersRepository managers;
    @Autowired private RolesRepository roles;
    @Autowired private UsersRepository users;
    @Autowired private ContractsRepository contracts;
    @Autowired private CounterpartiesRepository counterparties;
    @Autowired private BusinessAreasRepository areas;
    @Autowired private FinancialTypesRepository types;
    @Autowired private FinancialValuesRepository values;
    @Autowired private ContractManagerRepository collaborators;

    static Stream<Arguments> authorizationCases() {
        return Stream.of("financial").flatMap(endpoint -> Stream.of(
                Arguments.of(endpoint, "admin", 200),
                Arguments.of(endpoint, "assigned", 200),
                Arguments.of(endpoint, "colleague", 403),
                Arguments.of(endpoint, "collaboratorOnly", 403),
                Arguments.of(endpoint, "unassigned", 403),
                Arguments.of(endpoint, "noManagerProfile", 403),
                Arguments.of(endpoint, "foreignTenant", 404),
                Arguments.of(endpoint, "foreignTenantAdmin", 404),
                Arguments.of(endpoint, "missingContract", 404),
                Arguments.of(endpoint, "empty", 200),
                Arguments.of(endpoint, "missingTenant", 403),
                Arguments.of(endpoint, "userOutsideTenant", 403)));
    }

    @AfterEach
    void clearTenant() {
        TestTenant.clear();
        TenantContext.clear();
    }

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @MethodSource("authorizationCases")
    void shouldAuthorizeContractBeforeReturningResources(String endpoint, String scenario, int expectedStatus)
            throws Exception {
        Organization org = organization();
        Organization otherOrg = organization();
        Managers caller = manager(org);
        Managers owner = manager(org);
        Managers foreignOwner = manager(otherOrg);
        boolean admin = scenario.equals("admin") || scenario.equals("foreignTenantAdmin");
        String role = admin ? "ADMIN" : "MANAGER";
        Roles userRole = roles.findByRole(role)
                .orElseGet(() -> roles.save(Roles.builder().role(role).build()));
        String username = UUID.randomUUID().toString();
        users.save(Users.builder().organization(scenario.equals("userOutsideTenant") ? otherOrg : org)
                .username(username).passwordHash("test-password")
                .verified(true).role(userRole)
                .manager(scenario.equals("noManagerProfile") ? null : caller).build());

        boolean foreign = scenario.equals("foreignTenant") || scenario.equals("foreignTenantAdmin");
        Organization contractOrg = foreign ? otherOrg : org;
        Managers assigned = switch (scenario) {
            case "colleague", "collaboratorOnly" -> owner;
            case "unassigned" -> null;
            default -> foreign ? foreignOwner : caller;
        };
        BusinessAreas area = areas.save(BusinessAreas.builder().organization(contractOrg).name("Area").build());
        Counterparty counterparty = counterparties.save(Counterparty.builder().organization(contractOrg)
                .name("Counterparty").type(CounterpartyType.CUSTOMER).build());
        Contracts contract = contracts.save(Contracts.builder().organization(contractOrg)
                .contractNumber(UUID.randomUUID().toString()).counterparty(counterparty)
                .businessArea(area).manager(assigned).startDate(LocalDate.of(2026, Month.JANUARY, 1))
                .status(ContractStatus.ACTIVE).build());
        if (!scenario.equals("empty")) {
            FinancialTypes type = types.save(FinancialTypes.builder().organization(contractOrg)
                    .name("Revenue").category(FinancialCategory.REVENUE).build());
            values.save(FinancialValues.builder().organization(contractOrg).contract(contract)
                    .businessArea(area).financialType(type).month(1).year(2026).financialAmount(1234).build());
            Managers collaborator = switch (scenario) {
                case "foreignTenant", "foreignTenantAdmin" -> foreignOwner;
                case "colleague" -> owner;
                default -> caller;
            };
            collaborators.saveAndFlush(ContractManager.builder().contract(contract).manager(collaborator).build());
        }

        TestTenant.use(org.getId());
        if (scenario.equals("missingTenant")) {
            clearTenant();
        }
        UUID id = scenario.equals("missingContract") ? uid(Long.MAX_VALUE) : contract.getId();
        var response = mockMvc.perform(get("/financial-values/by-contract/{id}", id).with(user(username).roles(role)))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) {
            if (scenario.equals("empty")) {
                response.andExpect(content().json("[]"));
            } else if (endpoint.equals("financial")) {
                response.andExpect(jsonPath("$.length()").value(1))
                        .andExpect(jsonPath("$[0].financialAmount").value(1234));
            } else {
                response.andExpect(content().json("[" + caller.getId() + "]"));
            }
        }
    }

    private Organization organization() {
        return organizations.save(Organization.builder().name("Authorization test")
                .slug(UUID.randomUUID().toString()).build());
    }

    private Managers manager(Organization org) {
        return managers.save(Managers.builder().organization(org).firstName("Test").lastName("Manager")
                .email(UUID.randomUUID() + "@example.com").build());
    }
}
