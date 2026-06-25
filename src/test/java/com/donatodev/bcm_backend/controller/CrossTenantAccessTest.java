package com.donatodev.bcm_backend.controller;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.ContractSchedulerService;
import com.donatodev.bcm_backend.service.ExportService;
import com.donatodev.bcm_backend.util.TestDataCleaner;

/**
 * Regression tests guarding tenant isolation through the real authentication
 * path: a JWT minted for an organization (carrying its {@code orgId} claim)
 * must never expose another organization's contracts. Unlike most controller
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
    private ContractsRepository contractsRepository;

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
    private String orgAAdminToken;

    @BeforeEach
    void setUp() {
        testDataCleaner.clean();
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

        orgAContract = contractsRepository.save(Contracts.builder()
                .customerName("Org A Customer")
                .contractNumber("CNTR-ORG-A")
                .wbsCode("WBS-A")
                .projectName("Project A")
                .businessArea(area)
                .status(ContractStatus.ACTIVE)
                .startDate(LocalDate.of(2025, Month.JANUARY, 1))
                .endDate(LocalDate.of(2026, Month.JANUARY, 1))
                .organization(orgA)
                .build());

        orgBContract = contractsRepository.save(Contracts.builder()
                .customerName("Org B Customer")
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
    @DisplayName("Sanity check: both organizations' contracts exist in the database")
    void bothContractsExistAcrossOrganizations() {
        List<Contracts> all = contractsRepository.findAll();
        org.junit.jupiter.api.Assertions.assertEquals(2, all.size());
    }
}
