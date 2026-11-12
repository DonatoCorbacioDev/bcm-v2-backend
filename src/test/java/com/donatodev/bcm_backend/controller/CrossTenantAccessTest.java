package com.donatodev.bcm_backend.controller;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.repository.InviteTokenRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.ContractSchedulerService;
import com.donatodev.bcm_backend.service.ExportService;
import com.donatodev.bcm_backend.util.TestDataCleaner;

/**
 * Regression tests guarding tenant isolation through the real authentication
 * path: a JWT minted for an organization (carrying its {@code orgId} claim)
 * must never expose another organization's data, nor let a write link this
 * tenant's records to another tenant's (counterparty, area, manager...). Unlike most controller
 * tests in this suite, these do not use {@code @WithMockUser} — that
 * shortcut injects the principal directly into the security context and
 * skips {@link com.donatodev.bcm_backend.jwt.JwtAuthenticationFilter}
 * entirely, so it never populates
 * {@link com.donatodev.bcm_backend.config.TenantContext} and would not catch
 * a tenant-scoping regression.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Cross-tenant access regression tests")
class CrossTenantAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private BusinessAreasRepository businessAreasRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private ContractsRepository contractsRepository;

    @Autowired
    private CounterpartiesRepository counterpartiesRepository;

    @Autowired
    private FinancialTypesRepository financialTypesRepository;

    @Autowired
    private InviteTokenRepository inviteTokenRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TestDataCleaner testDataCleaner;

    @MockitoBean
    private ExportService exportService;

    @MockitoBean
    private ContractSchedulerService contractSchedulerService;

    private Contracts orgAContract;
    private Contracts orgBContract;
    private BusinessAreas orgAArea;
    private BusinessAreas orgBArea;
    private Managers orgBManager;
    private String orgAAdminToken;

    @BeforeEach
    void setUp() {
        testDataCleaner.clean();
        // TestDataCleaner predates the counterparties table.
        counterpartiesRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization orgA = organizationRepository.save(
                Organization.builder().name("Org A").slug("org-a").build());
        Organization orgB = organizationRepository.save(
                Organization.builder().name("Org B").slug("org-b").build());

        Roles adminRole = rolesRepository.save(Roles.builder().role("ADMIN").build());

        Users orgAAdmin = usersRepository.save(Users.builder()
                .username("admin-org-a")
                .passwordHash("irrelevant")
                .verified(true)
                .role(adminRole)
                .organization(orgA)
                .build());

        BusinessAreas area = businessAreasRepository.save(
                BusinessAreas.builder().name("Ops").description("Operations").build());

        orgAArea = businessAreasRepository.save(
                BusinessAreas.builder().name("Org A Area").description("Org A only").organization(orgA).build());
        orgBArea = businessAreasRepository.save(
                BusinessAreas.builder().name("Org B Area").description("Org B only").organization(orgB).build());
        orgBManager = managersRepository.save(Managers.builder()
                .firstName("Org B").lastName("Manager").email("orgb-manager@example.com")
                .organization(orgB).build());

        Counterparty orgACounterparty = counterpartiesRepository.save(Counterparty.builder().name("Org A Customer").type(CounterpartyType.CUSTOMER).build());
        orgAContract = contractsRepository.save(Contracts.builder()
                .counterparty(orgACounterparty)
                .contractNumber("CNTR-ORG-A")
                .wbsCode("WBS-A")
                .projectName("Project A")
                .businessArea(area)
                .status(ContractStatus.ACTIVE)
                .startDate(LocalDate.of(2025, Month.JANUARY, 1))
                .endDate(LocalDate.of(2026, Month.JANUARY, 1))
                .organization(orgA)
                .build());

        Counterparty orgBCounterparty = counterpartiesRepository.save(Counterparty.builder().name("Org B Customer").type(CounterpartyType.CUSTOMER).build());
        orgBContract = contractsRepository.save(Contracts.builder()
                .counterparty(orgBCounterparty)
                .contractNumber("CNTR-ORG-B")
                .wbsCode("WBS-B")
                .projectName("Project B")
                .businessArea(area)
                .status(ContractStatus.ACTIVE)
                .startDate(LocalDate.of(2025, Month.JANUARY, 1))
                .endDate(LocalDate.of(2026, Month.JANUARY, 1))
                .organization(orgB)
                .build());

        orgAAdminToken = jwtUtils.generateTokenFromUser(orgAAdmin);
    }

    @Test
    @DisplayName("GET /contracts/{id} returns 404 for a contract belonging to another organization")
    void getContractByIdRejectsOtherOrganizationContract() throws Exception {
        mockMvc.perform(get("/contracts/" + orgBContract.getId())
                        .header("Authorization", "Bearer " + orgAAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /contracts/{id} returns 200 for a contract belonging to the caller's own organization")
    void getContractByIdAllowsOwnOrganizationContract() throws Exception {
        mockMvc.perform(get("/contracts/" + orgAContract.getId())
                        .header("Authorization", "Bearer " + orgAAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractNumber").value("CNTR-ORG-A"));
    }

    @Test
    @DisplayName("GET /contracts only returns contracts scoped to the caller's organization (also guards the Excel/PDF export, which reuses this list)")
    void getAllContractsIsScopedToCallerOrganization() throws Exception {
        mockMvc.perform(get("/contracts")
                        .header("Authorization", "Bearer " + orgAAdminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contractNumber").value("CNTR-ORG-A"));
    }

    @Test
    @DisplayName("POST /contracts rejects a managerId belonging to another organization")
    void createContractRejectsOtherOrganizationManager() throws Exception {
        Counterparty counterparty = counterpartiesRepository.save(Counterparty.builder().name("New Customer").type(CounterpartyType.CUSTOMER).organization(orgAArea.getOrganization()).build());
        String body = """
                {
                  "counterpartyId": "%s",
                  "contractNumber": "CNTR-NEW-MANAGER",
                  "wbsCode": "WBS-NEW",
                  "projectName": "New Project",
                  "status": "ACTIVE",
                  "startDate": "2025-01-01",
                  "endDate": "2026-01-01",
                  "areaId": "%s",
                  "managerId": "%s"
                }
                """.formatted(counterparty.getId(), orgAArea.getId(), orgBManager.getId());

        mockMvc.perform(post("/contracts")
                        .header("Authorization", "Bearer " + orgAAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /contracts rejects an areaId belonging to another organization")
    void createContractRejectsOtherOrganizationArea() throws Exception {
        Counterparty counterparty = counterpartiesRepository.save(Counterparty.builder().name("New Customer").type(CounterpartyType.CUSTOMER).organization(orgAArea.getOrganization()).build());
        String body = """
                {
                  "counterpartyId": "%s",
                  "contractNumber": "CNTR-NEW-AREA",
                  "wbsCode": "WBS-NEW",
                  "projectName": "New Project",
                  "status": "ACTIVE",
                  "startDate": "2025-01-01",
                  "endDate": "2026-01-01",
                  "areaId": "%s"
                }
                """.formatted(counterparty.getId(), orgBArea.getId());

        mockMvc.perform(post("/contracts")
                        .header("Authorization", "Bearer " + orgAAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /contracts accepts a managerId/areaId belonging to the caller's own organization")
    void createContractAllowsOwnOrganizationManagerAndArea() throws Exception {
        Counterparty counterparty = counterpartiesRepository.save(Counterparty.builder().name("New Customer").type(CounterpartyType.CUSTOMER).organization(orgAArea.getOrganization()).build());
        String body = """
                {
                  "counterpartyId": "%s",
                  "contractNumber": "CNTR-NEW-OWN",
                  "wbsCode": "WBS-NEW",
                  "projectName": "New Project",
                  "status": "ACTIVE",
                  "startDate": "2025-01-01",
                  "endDate": "2026-01-01",
                  "areaId": "%s"
                }
                """.formatted(counterparty.getId(), orgAArea.getId());

        mockMvc.perform(post("/contracts")
                        .header("Authorization", "Bearer " + orgAAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.areaId").value(orgAArea.getId().toString()));
    }

    @Test
    @DisplayName("POST /users/invite rejects a manager of another organization (the invitee would join that organization)")
    void inviteRejectsOtherOrganizationManager() throws Exception {
        String body = """
                {"username": "intruder", "role": "MANAGER", "managerId": "%s"}
                """.formatted(orgBManager.getId());

        mockMvc.perform(post("/users/invite")
                        .header("Authorization", "Bearer " + orgAAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertTrue(inviteTokenRepository.findAll().stream().noneMatch(t -> "intruder".equals(t.getUsername())));
    }

    @Test
    @DisplayName("PUT /contracts/{id} rejects a counterparty, financial type or area of another organization")
    void updateContractRejectsOtherOrganizationReferences() throws Exception {
        Organization orgA = orgAArea.getOrganization();
        Organization orgB = orgBArea.getOrganization();
        Counterparty ownCounterparty = counterpartiesRepository.save(Counterparty.builder()
                .name("Own Customer").type(CounterpartyType.CUSTOMER).organization(orgA).build());
        Counterparty foreignCounterparty = counterpartiesRepository.save(Counterparty.builder()
                .name("Foreign Customer").vatNumber("IT99999999999").type(CounterpartyType.CUSTOMER).organization(orgB).build());
        FinancialTypes foreignType = financialTypesRepository.save(FinancialTypes.builder()
                .name("Foreign type").description("Org B only").category(FinancialCategory.REVENUE).organization(orgB).build());
        String template = """
                {
                  "counterpartyId": "%s",
                  "contractNumber": "CNTR-ORG-A",
                  "wbsCode": "WBS-A",
                  "projectName": "Project A",
                  "status": "ACTIVE",
                  "startDate": "2025-01-01",
                  "endDate": "2026-01-01",
                  "areaId": "%s"%s
                }
                """;

        for (String body : List.of(
                template.formatted(foreignCounterparty.getId(), orgAArea.getId(), ""),
                template.formatted(ownCounterparty.getId(), orgBArea.getId(), ""),
                template.formatted(ownCounterparty.getId(), orgAArea.getId(),
                        ", \"financialTypeId\": \"%s\", \"annualValue\": 1200, \"billingFrequency\": \"MONTHLY\"".formatted(foreignType.getId())))) {
            mockMvc.perform(put("/contracts/" + orgAContract.getId())
                            .header("Authorization", "Bearer " + orgAAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }

        Contracts unchanged = contractsRepository.findById(orgAContract.getId()).orElseThrow();
        assertNotEquals(foreignCounterparty.getId(), unchanged.getCounterparty().getId());
    }

    @Test
    @DisplayName("POST /contract-templates rejects an area or default manager of another organization")
    void createTemplateRejectsOtherOrganizationReferences() throws Exception {
        for (String relation : List.of(
                "\"businessAreaId\": \"" + orgBArea.getId() + "\"",
                "\"defaultManagerId\": \"" + orgBManager.getId() + "\"")) {
            mockMvc.perform(post("/contract-templates")
                            .header("Authorization", "Bearer " + orgAAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Leaky template\", " + relation + "}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("POST /budgets rejects an area of another organization")
    void createBudgetRejectsOtherOrganizationArea() throws Exception {
        mockMvc.perform(post("/budgets")
                        .header("Authorization", "Bearer " + orgAAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessAreaId\": \"%s\", \"category\": \"REVENUE\", \"year\": 2026, \"targetAmount\": 1000}"
                                .formatted(orgBArea.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /home/attention lists only the caller's organization's contracts")
    void homeAttentionIsScopedToCallerOrganization() throws Exception {
        LocalDate soon = LocalDate.now(java.time.ZoneId.systemDefault()).plusDays(10);
        orgAContract.setEndDate(soon);
        contractsRepository.save(orgAContract);
        orgBContract.setEndDate(soon);
        contractsRepository.save(orgBContract);

        mockMvc.perform(get("/home/attention")
                        .header("Authorization", "Bearer " + orgAAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].contractNumber").value("CNTR-ORG-A"))
                .andExpect(jsonPath("$.items[0].type").value("EXPIRY"))
                .andExpect(jsonPath("$.dueSoonCount").value(1));
    }

    @Test
    @DisplayName("Sanity check: both organizations' contracts exist in the database")
    void bothContractsExistAcrossOrganizations() {
        List<Contracts> all = contractsRepository.findAll();
        org.junit.jupiter.api.Assertions.assertEquals(2, all.size());
    }
}
